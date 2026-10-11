package ma.rhflow.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public final class WorkCalendar {

    private WorkCalendar() {
    }

    public static int workingDays(LocalDate start, LocalDate end, Set<DayOfWeek> work, Set<LocalDate> holidays) {
        if (start == null || end == null || end.isBefore(start) || work == null || work.isEmpty()) {
            return 0;
        }
        Set<LocalDate> off = holidays == null ? Set.of() : holidays;
        int n = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (work.contains(d.getDayOfWeek()) && !off.contains(d)) {
                n++;
            }
        }
        return n;
    }
}
