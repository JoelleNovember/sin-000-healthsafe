package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class WardCleaner {

    private static final int MAX_BEDS = 100;
    private static final Set<String> ACRONYMS = Set.of("ICU");
    private static final Map<String, String> DEPT_FIXES = Map.of("Pediatrics", "Paediatrics");
    private static final Map<String, Integer> NUMBER_WORDS = Map.of(
            "zero", 0, "one", 1, "two", 2, "three", 3, "four", 4,
            "five", 5, "six", 6, "seven", 7, "eight", 8, "nine", 9);

    public static List<Ward> load() throws Exception {
        Map<String, Ward> byId = new LinkedHashMap<>();
        // getResourceAsStream works inside the shaded jar; a file path would not
        try (var in = WardCleaner.class.getResourceAsStream("/wards-outdated.csv");
             var reader = new CSVReader(new InputStreamReader(in))) {
            reader.readNext(); // skip the header row
            String[] row;
            while ((row = reader.readNext()) != null) {
                Ward ward = cleanRow(row);
                byId.merge(ward.wardId(), ward, WardCleaner::merge);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static Ward cleanRow(String[] row) {
        List<String> notes = new ArrayList<>();
        String id = row[0].trim().toUpperCase();
        String wing = titleCase(row[1]);
        if (wing == null) notes.add("wing was missing");
        String dept = titleCase(row[2]);
        if (dept != null) dept = DEPT_FIXES.getOrDefault(dept, dept);
        Integer beds = parseBeds(row[3], notes);
        return new Ward(id, wing, dept, beds, String.join("; ", notes));
    }

    private static String titleCase(String raw) {
        String s = raw.trim().replaceAll("\\s+", " "); // trims and fixes double spaces
        if (s.isEmpty()) return null;
        return Arrays.stream(s.split(" "))
                .map(w -> ACRONYMS.contains(w.toUpperCase())
                        ? w.toUpperCase()
                        : w.substring(0, 1).toUpperCase() + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private static Integer parseBeds(String raw, List<String> notes) {
        String s = raw.trim().toLowerCase();
        if (NUMBER_WORDS.containsKey(s)) {
            notes.add("bedsAvailable '" + s + "' converted to " + NUMBER_WORDS.get(s));
            return NUMBER_WORDS.get(s);
        }
        try {
            int n = Integer.parseInt(s);
            if (n < 0 || n > MAX_BEDS) {
                notes.add("bedsAvailable " + n + " outside 0-" + MAX_BEDS + " - flagged");
                return null;
            }
            return n;
        } catch (NumberFormatException e) {
            notes.add("bedsAvailable was non-numeric ('" + raw.trim() + "') - flagged for follow-up");
            return null;
        }
    }

    // Two rows for the same ward: keep the first, fill any blanks from the second
    private static Ward merge(Ward a, Ward b) {
        return new Ward(a.wardId(),
                a.wing() != null ? a.wing() : b.wing(),
                a.department() != null ? a.department() : b.department(),
                a.bedsAvailable() != null ? a.bedsAvailable() : b.bedsAvailable(),
                a.notes() + " | duplicate row merged: " + b.notes());
    }
}