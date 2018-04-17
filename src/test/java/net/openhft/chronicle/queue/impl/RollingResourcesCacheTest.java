package net.openhft.chronicle.queue.impl;

import net.openhft.chronicle.queue.RollCycle;
import net.openhft.chronicle.queue.RollCycles;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class RollingResourcesCacheTest {
    private static final long SEED = 2983472039423847L;

    private static final long AM_EPOCH = 1523498933145L; //2018-04-12 02:08:53.145 UTC
    private static final int AM_DAILY_CYCLE_NUMBER = 1;
    private static final int AM_HOURLY_CYCLE_NUMBER = 24;
    private static final int AM_MINUTELY_CYCLE_NUMBER = 1568;
    private static final String AM_DAILY_FILE_NAME = "20180413";
    private static final String AM_HOURLY_FILE_NAME = "20180413-00";
    private static final String AM_MINUTELY_FILE_NAME = "20180413-0208";

    private static final long PM_EPOCH = 1284739200000L; //2010-09-17 16:00:00.000 UTC
    private static final int PM_DAILY_CYCLE_NUMBER = 2484;
    private static final int PM_HOURLY_CYCLE_NUMBER = PM_DAILY_CYCLE_NUMBER*24;
    private static final int PM_MINUTELY_CYCLE_NUMBER = PM_HOURLY_CYCLE_NUMBER*60;
    private static final String PM_DAILY_FILE_NAME = "20170706";
    private static final String PM_HOURLY_FILE_NAME = "20170706-00";
    private static final String PM_MINUTELY_FILE_NAME = "20170706-0000";

    private static final long POSITIVE_RELATIVE_EPOCH = 18000000L; // +5 hours
    private static final int POSITIVE_RELATIVE_DAILY_CYCLE_NUMBER = 2484;
    private static final int POSITIVE_RELATIVE_HOURLY_CYCLE_NUMBER = POSITIVE_RELATIVE_DAILY_CYCLE_NUMBER*24+15;
    private static final int POSITIVE_RELATIVE_MINUTELY_CYCLE_NUMBER = POSITIVE_RELATIVE_HOURLY_CYCLE_NUMBER*60+10;
    private static final String POSITIVE_RELATIVE_DAILY_FILE_NAME = "19761020";
    private static final String POSITIVE_RELATIVE_HOURLY_FILE_NAME = "19761020-15";
    private static final String POSITIVE_RELATIVE_MINUTELY_FILE_NAME = "19761020-1510";

    private static final long NEGATIVE_RELATIVE_EPOCH = -10800000L; // -3 hours
    private static final int NEGATIVE_RELATIVE_DAILY_CYCLE_NUMBER = 2484;
    private static final int NEGATIVE_RELATIVE_HOURLY_CYCLE_NUMBER = NEGATIVE_RELATIVE_DAILY_CYCLE_NUMBER*24+15;
    private static final int NEGATIVE_RELATIVE_MINUTELY_CYCLE_NUMBER = NEGATIVE_RELATIVE_HOURLY_CYCLE_NUMBER*60+10;
    private static final String NEGATIVE_RELATIVE_DAILY_FILE_NAME = "19761019";
    private static final String NEGATIVE_RELATIVE_HOURLY_FILE_NAME = "19761019-15";
    private static final String NEGATIVE_RELATIVE_MINUTELY_FILE_NAME = "19761019-1510";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long ONE_DAY_IN_MILLIS = TimeUnit.DAYS.toMillis(1L);
    private static final boolean LOG_TEST_DEBUG =
                Boolean.valueOf(RollingResourcesCacheTest.class.getSimpleName() + ".debug");

    @Test
    public void shouldConvertCyclesToResourceNamesWithNoEpoch() throws Exception {
        final int epoch = 0;
        final RollingResourcesCache cache =
                new RollingResourcesCache(RollCycles.DAILY, epoch, File::new, File::getName);

        final int cycle = RollCycles.DAILY.current(System::currentTimeMillis, 0);
        assertCorrectConversion(cache, cycle, Instant.now(),
                DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("GMT")));
    }

    private static void assertCorrectConversion(final RollingResourcesCache cache, final int cycle,
                                                final Instant instant, final DateTimeFormatter formatter) {
        final String expectedFileName = formatter.format(instant);
        assertThat(cache.resourceFor(cycle).text, is(expectedFileName));
        assertThat(cache.parseCount(expectedFileName), is(cycle));
    }

    private void doTestCycleAndResourceNames(long epoch, RollCycle rollCycle, int cycleNumber, String filename) {
        RollingResourcesCache cache =
                new RollingResourcesCache(rollCycle, epoch, File::new, File::getName);
        assertThat(cache.resourceFor(cycleNumber).text, is(filename));
        assertThat(cache.parseCount(filename), is(cycleNumber));
    }

    @Test
    public void shouldCorrectlyConvertCyclesToResourceNamesWithEpoch() throws Exception {
        // AM_EPOCH is 2018-04-12 02:08:53.145 UTC
        // cycle 24 should be formatted as:
        // 2018-04-12 00:00:00 UTC (1523491200000) +
        // Timezone offset 02:08:53.145 (7733145)
        // 24 hourly cycles (24 * 3_600_000) =
        // 1523585333145 = Friday, 13 April 2018 02:08:53.145 UTC ie. filename is 20180413-00 local
        doTestCycleAndResourceNames(AM_EPOCH, RollCycles.DAILY, AM_DAILY_CYCLE_NUMBER, AM_DAILY_FILE_NAME);
        doTestCycleAndResourceNames(AM_EPOCH, RollCycles.HOURLY, AM_HOURLY_CYCLE_NUMBER, AM_HOURLY_FILE_NAME);
        doTestCycleAndResourceNames(AM_EPOCH, RollCycles.MINUTELY, AM_MINUTELY_CYCLE_NUMBER, AM_MINUTELY_FILE_NAME);

        // PM_EPOCH is 2010-09-17 16:00:00.000 UTC
        // cycle 2484 should be formatted as:
        // 2010-09-17 00:00:00 UTC (1284681600000) +
        // Timezone offset 16:00:00.000 (57600000)
        // 2484 daily cycles (2484 * 86_400_000 = 214617600000)
        // 1499356800000 = Thursday, 6 July 2017 16:00:00 UTC ie. filename is 20170706 local
        doTestCycleAndResourceNames(PM_EPOCH, RollCycles.DAILY, PM_DAILY_CYCLE_NUMBER, PM_DAILY_FILE_NAME);
        doTestCycleAndResourceNames(PM_EPOCH, RollCycles.HOURLY, PM_HOURLY_CYCLE_NUMBER, PM_HOURLY_FILE_NAME);
        doTestCycleAndResourceNames(PM_EPOCH, RollCycles.MINUTELY, PM_MINUTELY_CYCLE_NUMBER, PM_MINUTELY_FILE_NAME);

        // POSITIVE_RELATIVE_EPOCH is 5 hours (18000000 millis)
        // cycle 2484 should be formatted as:
        // epoch 1970-01-01 00:00:00 (0) +
        // Timezone offset 05:00:00 (18000000)
        // 2484 daily cycles (2484 * 86_400_000 = 214617600000) =
        // 214635600000 - Wednesday, 20 October 1976 05:00:00 UTC ie. filename is 19761020 local
        doTestCycleAndResourceNames(POSITIVE_RELATIVE_EPOCH, RollCycles.DAILY, POSITIVE_RELATIVE_DAILY_CYCLE_NUMBER, POSITIVE_RELATIVE_DAILY_FILE_NAME);
        doTestCycleAndResourceNames(POSITIVE_RELATIVE_EPOCH, RollCycles.HOURLY, POSITIVE_RELATIVE_HOURLY_CYCLE_NUMBER, POSITIVE_RELATIVE_HOURLY_FILE_NAME);
        doTestCycleAndResourceNames(POSITIVE_RELATIVE_EPOCH, RollCycles.MINUTELY, POSITIVE_RELATIVE_MINUTELY_CYCLE_NUMBER, POSITIVE_RELATIVE_MINUTELY_FILE_NAME);

        // NEGATIVE_RELATIVE_EPOCH is -3 hours (-10800000 millis)
        // cycle 2484 should be formatted as:
        // epoch 1969-12-31 00:00:00 (-86400000) +
        // Timezone offset -03:00:00 (-10800000)
        // 2484 daily cycles (2484 * 86_400_000 = 214617600000) =
        // 214520400000 - Monday, 18 October 1976 21:00:00 UTC ie. filename is 19761019 local
        doTestCycleAndResourceNames(NEGATIVE_RELATIVE_EPOCH, RollCycles.DAILY, NEGATIVE_RELATIVE_DAILY_CYCLE_NUMBER, NEGATIVE_RELATIVE_DAILY_FILE_NAME);
        doTestCycleAndResourceNames(NEGATIVE_RELATIVE_EPOCH, RollCycles.HOURLY, NEGATIVE_RELATIVE_HOURLY_CYCLE_NUMBER, NEGATIVE_RELATIVE_HOURLY_FILE_NAME);
        doTestCycleAndResourceNames(NEGATIVE_RELATIVE_EPOCH, RollCycles.MINUTELY, NEGATIVE_RELATIVE_MINUTELY_CYCLE_NUMBER, NEGATIVE_RELATIVE_MINUTELY_FILE_NAME);
    }

    @Test(expected = RuntimeException.class)
    public void parseIncorrectlyFormattedName() throws Exception {
        final RollingResourcesCache cache =
                new RollingResourcesCache(RollCycles.HOURLY, PM_EPOCH, File::new, File::getName);
        cache.parseCount("foobar-qux");
    }

    @Test
    public void fuzzyConversionTest() throws Exception {
        final int maxAddition = (int) ChronoUnit.DECADES.getDuration().toMillis();
        final Random random = new Random(SEED);

        for (int i = 0; i < 1_000; i++) {
            final long epoch = random.nextInt(maxAddition);
            final RollingResourcesCache cache =
                    new RollingResourcesCache(RollCycles.DAILY, epoch, File::new, File::getName);

            for (int j = 0; j < 200; j++) {
                final long offsetMillisFromEpoch =
                        TimeUnit.DAYS.toMillis(random.nextInt(500)) +
                                TimeUnit.HOURS.toMillis(random.nextInt(50)) +
                                TimeUnit.MINUTES.toMillis(random.nextInt(50));

                final long instantAfterEpoch = epoch + offsetMillisFromEpoch;
                final ZoneId zoneId = ZoneId.of("UTC");

                final int cycle = RollCycles.DAILY.current(() -> instantAfterEpoch, epoch);

                final long daysBetweenEpochAndInstant = (instantAfterEpoch - epoch) / ONE_DAY_IN_MILLIS;

                assertThat((long) cycle, is(daysBetweenEpochAndInstant));

                assertThat(((long) cycle) * ONE_DAY_IN_MILLIS,
                        is((long) cycle * RollCycles.DAILY.length()));

                if (LOG_TEST_DEBUG) {
                    System.out.printf("Epoch: %d%n", epoch);
                    System.out.printf("Epoch millis: %d(UTC+%dd), current millis: %d(UTC+%dd)%n",
                            epoch, (epoch / ONE_DAY_IN_MILLIS), instantAfterEpoch,
                            (instantAfterEpoch / ONE_DAY_IN_MILLIS));
                    System.out.printf("Delta days: %d, Delta millis: %d, Delta days in millis: %d%n",
                            daysBetweenEpochAndInstant,
                            instantAfterEpoch - epoch,
                            daysBetweenEpochAndInstant * ONE_DAY_IN_MILLIS);
                    System.out.printf("MillisSinceEpoch: %d%n",
                            offsetMillisFromEpoch);
                    System.out.printf("Resource calc of millisSinceEpoch: %d%n",
                            daysBetweenEpochAndInstant * ONE_DAY_IN_MILLIS);
                }

                long effectiveCycleStartTime = (instantAfterEpoch - epoch) -
                        ((instantAfterEpoch - epoch) % ONE_DAY_IN_MILLIS);

                assertCorrectConversion(cache, cycle,
                        Instant.ofEpochMilli(effectiveCycleStartTime + epoch),
                        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(zoneId));
            }
        }
    }

    @Test
    public void testToLong() {
        doTestToLong(RollCycles.DAILY, AM_EPOCH, 0, Long.valueOf("17633"));
        doTestToLong(RollCycles.HOURLY, AM_EPOCH, 0, Long.valueOf("423192"));
        doTestToLong(RollCycles.MINUTELY, AM_EPOCH, 0, Long.valueOf("25391520"));
        doTestToLong(RollCycles.DAILY, AM_EPOCH, 100, Long.valueOf("17733"));
        doTestToLong(RollCycles.HOURLY, AM_EPOCH, 100, Long.valueOf("423292"));
        doTestToLong(RollCycles.MINUTELY, AM_EPOCH, 100, Long.valueOf("25391620"));

        doTestToLong(RollCycles.DAILY, PM_EPOCH, 0, Long.valueOf("14869"));
        doTestToLong(RollCycles.HOURLY, PM_EPOCH, 0, Long.valueOf("356856"));
        doTestToLong(RollCycles.MINUTELY, PM_EPOCH, 0, Long.valueOf("21411360"));
        doTestToLong(RollCycles.DAILY, PM_EPOCH, 100, Long.valueOf("14969"));
        doTestToLong(RollCycles.HOURLY, PM_EPOCH, 100, Long.valueOf("356956"));
        doTestToLong(RollCycles.MINUTELY, PM_EPOCH, 100, Long.valueOf("21411460"));

        doTestToLong(RollCycles.DAILY, POSITIVE_RELATIVE_EPOCH, 0, Long.valueOf("0"));
        doTestToLong(RollCycles.HOURLY, POSITIVE_RELATIVE_EPOCH, 0, Long.valueOf("0"));
        doTestToLong(RollCycles.MINUTELY, POSITIVE_RELATIVE_EPOCH, 0, Long.valueOf("0"));
        doTestToLong(RollCycles.DAILY, POSITIVE_RELATIVE_EPOCH, 100, Long.valueOf("100"));
        doTestToLong(RollCycles.HOURLY, POSITIVE_RELATIVE_EPOCH, 100, Long.valueOf("100"));
        doTestToLong(RollCycles.MINUTELY, POSITIVE_RELATIVE_EPOCH, 100, Long.valueOf("100"));

        doTestToLong(RollCycles.DAILY, NEGATIVE_RELATIVE_EPOCH, 0, Long.valueOf("-1"));
        doTestToLong(RollCycles.HOURLY, NEGATIVE_RELATIVE_EPOCH, 0, Long.valueOf("-24"));
        doTestToLong(RollCycles.MINUTELY, NEGATIVE_RELATIVE_EPOCH, 0, Long.valueOf("-1440"));
        doTestToLong(RollCycles.DAILY, NEGATIVE_RELATIVE_EPOCH, 100, Long.valueOf("99"));
        doTestToLong(RollCycles.HOURLY, NEGATIVE_RELATIVE_EPOCH, 100, Long.valueOf("76"));
        doTestToLong(RollCycles.MINUTELY, NEGATIVE_RELATIVE_EPOCH, 100, Long.valueOf("-1340"));
    }

    public void doTestToLong(RollCycle rollCycle, long epoch, long cycle, Long expectedLong) {
        RollingResourcesCache cache =
                new RollingResourcesCache(rollCycle, epoch, File::new, File::getName);

        RollingResourcesCache.Resource resource = cache.resourceFor(cycle);
        assertEquals(expectedLong, cache.toLong(resource.path));
    }
}