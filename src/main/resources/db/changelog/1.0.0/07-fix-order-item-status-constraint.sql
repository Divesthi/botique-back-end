-- Fix order_item_details status check constraint to include 'fresh' instead of 'new'
ALTER TABLE order_item_details DROP CONSTRAINT order_item_details_status_check;
ALTER TABLE order_item_details ADD CONSTRAINT order_item_details_status_check
    CHECK (status IN ('fresh', 'in_progress', 'completed', 'delivered'));

-- Update any existing rows that have status = 'new' to 'fresh'
UPDATE order_item_details SET status = 'fresh' WHERE status = 'new';
