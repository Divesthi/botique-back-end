package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.TenantModel;
import com.dreamworks.bqom.model.notification.TenantPreferencesRequest;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.entity.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for tenant management — extended with preferences deep-merge.
 *
 * <h2>Deep-merge design</h2>
 * <p>The PATCH semantics for {@code preferences} are:
 * <ul>
 *   <li><b>New keys</b> in the patch are added to the stored preferences.</li>
 *   <li><b>Existing keys</b> present in both patch and DB are recursively merged
 *       (nested maps are merged, not replaced).</li>
 *   <li><b>Keys only in the DB</b> are preserved unchanged.</li>
 *   <li><b>Null values</b> in the patch explicitly remove the corresponding key.</li>
 * </ul>
 *
 * <p>Example — DB contains:
 * <pre>{@code { "notifications": { "channel": "whatsapp" }, "theme": "dark" }}</pre>
 * PATCH body:
 * <pre>{@code { "notifications": { "channel": "telegram" } }}</pre>
 * Result:
 * <pre>{@code { "notifications": { "channel": "telegram" }, "theme": "dark" }}</pre>
 *
 * <h2>Why a recursive map merge over Jackson's ObjectReader?</h2>
 * <p>{@code ObjectMapper.readerForUpdating()} merges at the object level but
 * replaces arrays and has known edge cases with {@code null} values in nested
 * structures. A recursive map merge is:
 * <ul>
 *   <li>Explicit in its semantics — no framework surprises.</li>
 *   <li>Faster — no serialization round-trip for an already-materialised
 *       {@code Map<String, Object>} (Hibernate loads preferences as a map).</li>
 *   <li>Easier to test in isolation.</li>
 * </ul>
 */
@Service
@Slf4j
public class TenantService {

    @Autowired
    private TenantRepository tenantRepository;

    // ── existing methods ───────────────────────────────────────────────────────

    public List<TenantModel> getTenants() {
        return tenantRepository.findAll().stream().map(this::toModel).toList();
    }

    public TenantModel getTenant(String code) {
        return tenantRepository.findByCode(code)
                .map(this::toModel)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + code));
    }

    public TenantModel createTenant(TenantModel model) {
        tenantRepository.findByCode(model.getCode()).ifPresent(t -> {
            throw new RuntimeException("Tenant with code " + model.getCode() + " already exists");
        });
        Tenant tenant = toEntity(model);
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);
        log.info("Tenant created: {}", tenant.getCode());
        return toModel(tenant);
    }

    public TenantModel updateTenant(TenantModel model) {
        Tenant tenant = tenantRepository.findByCode(model.getCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + model.getCode()));
        if (model.getName() != null)        tenant.setName(model.getName());
        if (model.getAddress() != null)     tenant.setAddress(model.getAddress());
        if (model.getPhoneNumber() != null) tenant.setPhoneNumber(model.getPhoneNumber());
        if (model.getStartedDate() != null) tenant.setStartedDate(model.getStartedDate());
        if (model.getChurnedDate() != null) tenant.setChurnedDate(model.getChurnedDate());
        if (model.getActive() != null)      tenant.setActive(model.getActive());
        tenant = tenantRepository.save(tenant);
        log.info("Tenant updated: {}", tenant.getCode());
        return toModel(tenant);
    }

    // ── NEW: preferences PATCH ─────────────────────────────────────────────────

    /**
     * Deep-merges the supplied preferences into the tenant's existing
     * {@code preferences} JSONB column and persists the result.
     *
     * <p>The merge is performed entirely in-memory on the materialised
     * {@code Map<String, Object>} that Hibernate provides — no extra
     * serialisation round-trip.
     *
     * @param tenantCode tenant to update
     * @param request    the incoming PATCH body
     * @return the full merged preferences map (as stored)
     * @throws RuntimeException if the tenant does not exist
     */
    @Transactional
    public Map<String, Object> updatePreferences(String tenantCode,
                                                 TenantPreferencesRequest request) {
        Tenant tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantCode));

        Map<String, Object> incoming = buildPreferencesMap(request);

        // Merge incoming into existing — existing keys not in the patch survive
        Map<String, Object> merged = deepMerge(tenant.getPreferences(), incoming);
        tenant.setPreferences(merged);

        tenantRepository.save(tenant);

        log.info("[Preferences] Updated preferences for tenant={}, mergedKeys={}",
                tenantCode, merged.keySet());

        return merged;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Converts a {@link TenantPreferencesRequest} into a raw
     * {@code Map<String, Object>} so the recursive merge can work uniformly
     * on plain maps — no typed-object vs map impedance mismatch.
     *
     * <p>Only non-null fields are included so absent fields in the PATCH body
     * don't accidentally null-out existing preferences.
     */
    private Map<String, Object> buildPreferencesMap(TenantPreferencesRequest request) {
        Map<String, Object> map = new HashMap<>();

        if (request.getNotifications() != null) {
            Map<String, Object> notifications = new HashMap<>();
            if (request.getNotifications().getChannel() != null) {
                notifications.put("channel",
                        request.getNotifications().getChannel().getValue());
            }
            map.put("notifications", notifications);
        }

        // Preserve any extra keys from the request body that aren't explicitly modelled
        if (request.getExtras() != null) {
            map.putAll(request.getExtras());
        }

        return map;
    }

    /**
     * Recursively merges {@code incoming} into {@code base}.
     *
     * <p>Rules:
     * <ul>
     *   <li>If a key exists in both and both values are maps → recurse.</li>
     *   <li>If a key exists in both and either value is not a map → incoming wins.</li>
     *   <li>If a key exists only in base → preserved.</li>
     *   <li>If a key exists only in incoming → added.</li>
     *   <li>If incoming value is {@code null} → key is removed from result.</li>
     * </ul>
     *
     * <p>A new {@code HashMap} is returned on every call — the inputs are
     * never mutated, making the function safe to call concurrently and easy
     * to test deterministically.
     *
     * @param base     the existing stored preferences (may be null/empty)
     * @param incoming the patch data (may be null/empty)
     * @return merged result
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> deepMerge(Map<String, Object> base,
                                         Map<String, Object> incoming) {
        // Start with a shallow copy of base so we don't mutate the entity's map
        Map<String, Object> result = new HashMap<>(base != null ? base : Map.of());

        if (incoming == null || incoming.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key   = entry.getKey();
            Object inVal = entry.getValue();

            if (inVal == null) {
                // Explicit null in the patch → remove the key
                result.remove(key);
                continue;
            }

            Object baseVal = result.get(key);

            if (baseVal instanceof Map && inVal instanceof Map) {
                // Both sides are maps → recurse to merge nested objects
                result.put(key, deepMerge(
                        (Map<String, Object>) baseVal,
                        (Map<String, Object>) inVal
                ));
            } else {
                // Scalar or array → incoming wins
                result.put(key, inVal);
            }
        }

        return result;
    }

    // ── mapping ────────────────────────────────────────────────────────────────

    private TenantModel toModel(Tenant t) {
        return TenantModel.builder()
                .id(t.getId())
                .code(t.getCode())
                .name(t.getName())
                .address(t.getAddress())
                .phoneNumber(t.getPhoneNumber())
                .startedDate(t.getStartedDate())
                .churnedDate(t.getChurnedDate())
                .active(t.getActive())
                .preferences(t.getPreferences())
                .build();
    }

    private Tenant toEntity(TenantModel model) {
        return Tenant.builder()
                .name(model.getName())
                .code(model.getCode())
                .address(model.getAddress())
                .phoneNumber(model.getPhoneNumber())
                .startedDate(model.getStartedDate())
                .churnedDate(model.getChurnedDate())
                .active(model.getActive())
                .preferences(model.getPreferences())
                .build();
    }
}