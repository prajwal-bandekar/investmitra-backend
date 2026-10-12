-- Cleanup of BSE bonds / NCDs / InvITs that earlier bhavcopy imports created as stocks.
-- NOT RUN BY ANYONE YET. Review the preview (step 1) before running steps 2-3.
--
-- The database does not store the BSE group (SctySrs), so candidates are identified by name:
-- bond-like names (a coupon percent sign, ending BOND / NCD, TAXFREE, -PVT, -ZC-) and InvIT / REIT /
-- infrastructure-trust names. A candidate must also be sector 'Other' with no Alpha Vantage symbol (so it
-- was created by the import, never hand-curated) and must not be referenced by any holding, trade or
-- watchlist item. Real companies such as "Chembond" or "Bondada" are NOT matched.
-- Some InvITs and bonds with unusual names will survive; extend the patterns after reading the preview.
--
-- Run on a copy first, or inside the transaction below, and COMMIT only after checking the counts.

-- Step 1: preview. Read every row. Anything that is a real company must be excluded before step 2.
SELECT s.id, s.symbol, s.name, s.isin
FROM stock s
WHERE s.sector = 'Other'
  AND s.av_symbol IS NULL
  AND (   s.name LIKE '%\%%'
       OR s.name LIKE '%-BOND'
       OR s.name LIKE '%NCD'
       OR s.name LIKE '%TAXFREE%'
       OR s.name LIKE '%-PVT'
       OR s.name LIKE '%-ZC-%'
       OR s.name LIKE '% InvIT%'
       OR s.name LIKE '%REIT'
       OR s.name LIKE '%Infrastructure Trust%'
       OR s.name LIKE '%Infra Trust%')
  AND NOT EXISTS (SELECT 1 FROM holding h WHERE h.stock_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM trade t WHERE t.stock_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM watchlist_item w WHERE w.stock_id = s.id)
ORDER BY s.name;

-- Step 2: delete (same criteria). Prices first, then the stocks.
START TRANSACTION;

CREATE TEMPORARY TABLE bond_candidates AS
SELECT s.id
FROM stock s
WHERE s.sector = 'Other'
  AND s.av_symbol IS NULL
  AND (   s.name LIKE '%\%%'
       OR s.name LIKE '%-BOND'
       OR s.name LIKE '%NCD'
       OR s.name LIKE '%TAXFREE%'
       OR s.name LIKE '%-PVT'
       OR s.name LIKE '%-ZC-%'
       OR s.name LIKE '% InvIT%'
       OR s.name LIKE '%REIT'
       OR s.name LIKE '%Infrastructure Trust%'
       OR s.name LIKE '%Infra Trust%')
  AND NOT EXISTS (SELECT 1 FROM holding h WHERE h.stock_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM trade t WHERE t.stock_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM watchlist_item w WHERE w.stock_id = s.id);

SELECT COUNT(*) AS stocks_to_delete FROM bond_candidates;

DELETE FROM stock_price WHERE stock_id IN (SELECT id FROM bond_candidates);
DELETE FROM stock WHERE id IN (SELECT id FROM bond_candidates);

DROP TEMPORARY TABLE bond_candidates;

-- Step 3: if the counts match the preview, run COMMIT; otherwise run ROLLBACK.
-- COMMIT;
-- ROLLBACK;
