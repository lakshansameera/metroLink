package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import org.junit.Test;

public class RouteTest {

    private static List<RouteStop> r100Stops() {
        return List.of(
                new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3),
                new RouteStop("S4", 4, 7.4), new RouteStop("S5", 5, 10.8), new RouteStop("S6", 6, 14.5),
                new RouteStop("S7", 7, 22.0));
    }

    private static Route r100() {
        return new Route("R100", "Colombo Fort - Ganemulla", r100Stops());
    }

    @Test
    public void theOriginAndDestinationAreTheFirstAndLastStops() {
        Route route = r100();

        assertEquals("S1", route.getOriginStopId());
        assertEquals("S7", route.getDestinationStopId());
    }

    @Test
    public void stopsAreKeptInTravelOrderWhateverOrderTheyWereGiven() {
        List<RouteStop> shuffled = new ArrayList<>(r100Stops());
        java.util.Collections.reverse(shuffled);

        Route route = new Route("R100", "Reversed input", shuffled);

        assertEquals("S1", route.getOriginStopId());
        assertEquals("S7", route.getDestinationStopId());
    }

    @Test
    public void distanceIsMeasuredFromBoardingToTheActualExitStop() {      // X3
        Route route = r100();

        assertEquals(10.8, route.distanceKm("S1", "S5"), 0.0001);
        assertEquals(7.4, route.distanceKm("S1", "S4"), 0.0001);
        assertEquals(8.7, route.distanceKm("S2", "S5"), 0.0001);
    }

    @Test
    public void floatingPointNoiseIsRemovedFromDistances() {
        // 10.8 - 2.1 is 8.700000000000001 as a double. The route must hand back exactly 8.7.
        assertEquals(8.7, r100().distanceKm("S2", "S5"), 0.0);
    }

    @Test
    public void aStopThatIsNotOnTheRouteIsRefused() {                      // X2
        Route route = r100();

        DomainException boarding = assertThrows(DomainException.class, () -> route.distanceKm("S9", "S5"));
        DomainException exit = assertThrows(DomainException.class, () -> route.distanceKm("S1", "S9"));

        assertEquals("STOP_NOT_ON_ROUTE", boarding.code());
        assertEquals("STOP_NOT_ON_ROUTE", exit.code());
    }

    @Test
    public void theExitMustComeAfterTheBoardingStop() {                    // X2
        Route route = r100();

        DomainException same = assertThrows(DomainException.class, () -> route.distanceKm("S3", "S3"));
        DomainException backwards = assertThrows(DomainException.class, () -> route.distanceKm("S5", "S2"));

        assertEquals("EXIT_NOT_AFTER_BOARDING", same.code());
        assertEquals("EXIT_NOT_AFTER_BOARDING", backwards.code());
    }

    @Test
    public void itKnowsWhichStopsAreOnTheRouteAndTheirOrder() {            // B4
        Route route = r100();

        assertTrue(route.hasStop("S4"));
        assertFalse(route.hasStop("S9"));
        assertTrue(route.isBefore("S1", "S5"));
        assertFalse(route.isBefore("S5", "S1"));
        assertFalse(route.isBefore("S3", "S3"));
        assertFalse(route.isBefore("S1", "S9"));
    }

    @Test
    public void theStopListCannotBeChangedFromOutside() {
        List<RouteStop> given = new ArrayList<>(r100Stops());
        Route route = new Route("R100", "Copy", given);

        given.clear();

        assertEquals(7, route.getStops().size());
        assertThrows(UnsupportedOperationException.class, () -> route.getStops().clear());
    }

    @Test
    public void aRouteNeedsAtLeastTwoStops() {
        assertThrows(IllegalArgumentException.class,
                () -> new Route("R1", "One stop", List.of(new RouteStop("S1", 1, 0))));
    }
}
