/**
 * AIO Platform — Group Seed (Batch Registration)
 * ==============================================
 * File: aio-platform/src/main/java/ae/ac/cud/customs/GroupSeedPost.java
 * Role: POST /custom/seed-groups — reads bundled Excel file from classpath,
 *       creates Authority groups (no external file required)
 * Registered in: webscript-context.xml
 */
package ae.ac.cud.customs;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class GroupSeedPost extends DeclarativeWebScript {

    private static final int MAX_GROUPS_PER_BATCH = 1000;

    /**
     * Bundled Excel seed file — lives under src/main/resources at the
     * same package path as this class, so class-relative resolution works.
     * The file is placed at:
     *   aio-platform/src/main/resources/ae/ac/cud/customs/alfresco_seed_groups.xlsx
     * The POM's resource block excludes *.xlsx from filtering (binary-safe).
     *
     * Loaded at runtime with class-relative resolution:
     *   getClass().getResourceAsStream("alfresco_seed_groups.xlsx")
     * This resolves to:
     *   ae/ac/cud/customs/alfresco_seed_groups.xlsx  on the classpath
     */
    private static final String SEED_FILENAME = "alfresco_seed_group_data.xlsx";

    private AuthorityService authorityService;

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        JSONObject result = new JSONObject();
        List<JSONObject> results = new ArrayList<>();
        int created = 0, skipped = 0, errors = 0, totalRows = 0;

        try {
            // Load the bundled Excel file from classpath (class-relative —
            // resolves next to GroupSeedPost.class in the same package,
            // immune to Maven resource filtering)
            InputStream is = getClass().getResourceAsStream(SEED_FILENAME);
            if (is == null) {
                status.setCode(Status.STATUS_INTERNAL_SERVER_ERROR);
                result.put("error", "Bundled seed file not found in package: " +
                        getClass().getPackage().getName() + "/" + SEED_FILENAME);
                model.put("result", result.toString(2));
                return model;
            }

            Workbook workbook;
            try {
                workbook = WorkbookFactory.create(is);
            } finally {
                is.close();
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                status.setCode(Status.STATUS_INTERNAL_SERVER_ERROR);
                result.put("error", "No sheet found in bundled Excel file");
                model.put("result", result.toString(2));
                workbook.close();
                return model;
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                status.setCode(Status.STATUS_INTERNAL_SERVER_ERROR);
                result.put("error", "Excel file has no header row");
                model.put("result", result.toString(2));
                workbook.close();
                return model;
            }

            Map<String, Integer> colMap = buildColumnMap(headerRow);
            String[] required = {"groupid", "displayname"};
            for (String reqCol : required) {
                if (!colMap.containsKey(reqCol)) {
                    status.setCode(Status.STATUS_BAD_REQUEST);
                    result.put("error", "Missing required column: " + reqCol);
                    result.put("expected_columns", new JSONArray(required));
                    result.put("found_columns", new JSONArray(colMap.keySet()));
                    model.put("result", result.toString(2));
                    workbook.close();
                    return model;
                }
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                totalRows++;
                if (totalRows > MAX_GROUPS_PER_BATCH) {
                    result.put("warning", "Reached batch limit of " + MAX_GROUPS_PER_BATCH + ". Remaining rows skipped.");
                    break;
                }
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    SeedGroup group = extractGroup(row, colMap);
                    if (group == null || StringUtils.isBlank(group.groupId) || StringUtils.isBlank(group.displayName)) {
                        skipped++;
                        results.add(skipEntry(i + 1, "Missing required fields"));
                        continue;
                    }

                    // Normalize groupId: auto-prefix with GROUP_ if not already
                    String authorityName = group.groupId.trim();
                    if (!authorityName.toUpperCase().startsWith("GROUP_")) {
                        authorityName = "GROUP_" + authorityName;
                    }

                    if (authorityService.authorityExists(authorityName)) {
                        skipped++;
                        JSONObject s = skipEntry(i + 1, "Group already exists");
                        s.put("authorityName", authorityName);
                        results.add(s);
                        continue;
                    }

                    // Create the group
                    authorityService.createAuthority(AuthorityType.GROUP, authorityName, group.displayName.trim(), null);

                    // Handle parent group if specified
                    String parentName = null;
                    if (StringUtils.isNotBlank(group.parentGroup)) {
                        parentName = group.parentGroup.trim();
                        if (!parentName.toUpperCase().startsWith("GROUP_")) {
                            parentName = "GROUP_" + parentName;
                        }
                        if (authorityService.authorityExists(parentName)) {
                            authorityService.addAuthority(parentName, authorityName);
                        } else {
                            // Parent doesn't exist yet — create it first, then link
                            authorityService.createAuthority(AuthorityType.GROUP, parentName, parentName, null);
                            authorityService.addAuthority(parentName, authorityName);
                        }
                    }

                    JSONObject ok = new JSONObject();
                    ok.put("row", i + 1);
                    ok.put("status", "created");
                    ok.put("authorityName", authorityName);
                    ok.put("displayName", group.displayName.trim());
                    if (parentName != null) {
                        ok.put("parentGroup", parentName);
                    }
                    results.add(ok);
                    created++;

                } catch (Exception e) {
                    errors++;
                    JSONObject err = new JSONObject();
                    err.put("row", i + 1);
                    err.put("status", "error");
                    err.put("reason", e.getMessage());
                    results.add(err);
                }
            }
            workbook.close();

        } catch (Exception e) {
            status.setCode(Status.STATUS_INTERNAL_SERVER_ERROR);
            result.put("error", "Failed to process seed file: " + e.getMessage());
            model.put("result", result.toString(2));
            return model;
        }

        result.put("total_rows", totalRows);
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("errors", errors);
        result.put("groups", new JSONArray(results.toString()));
        model.put("result", result.toString(2));
        return model;
    }

    // ============== Excel Helpers ==============

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String h = cellStr(cell).toLowerCase().trim();
                if (!h.isEmpty()) map.put(h, i);
            }
        }
        return map;
    }

    private SeedGroup extractGroup(Row row, Map<String, Integer> colMap) {
        SeedGroup g = new SeedGroup();
        g.groupId = getValue(row, colMap, "groupid");
        g.displayName = getValue(row, colMap, "displayname");
        g.parentGroup = getValue(row, colMap, "parentgroup");
        return g;
    }

    private String getValue(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        return idx == null ? null : cellStr(row.getCell(idx));
    }

    private String cellStr(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toString();
                double d = cell.getNumericCellValue();
                return (d == Math.floor(d) && !Double.isInfinite(d) && d < Long.MAX_VALUE)
                        ? String.valueOf((long) d) : String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue().trim(); } catch (Exception e) {
                    try { return String.valueOf(cell.getNumericCellValue()); } catch (Exception e2) {
                        return cell.getCellFormula();
                    }
                }
            default: return null;
        }
    }

    private JSONObject skipEntry(int row, String reason) {
        JSONObject s = new JSONObject();
        s.put("row", row);
        s.put("status", "skipped");
        s.put("reason", reason);
        return s;
    }

    // ============== Data Class ==============

    private static class SeedGroup {
        String groupId, displayName, parentGroup;
    }
}