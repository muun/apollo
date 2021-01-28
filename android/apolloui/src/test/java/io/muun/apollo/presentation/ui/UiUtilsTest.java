package io.muun.apollo.presentation.ui;

import io.muun.apollo.domain.utils.DateUtils;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import org.junit.Test;
import org.threeten.bp.ZonedDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class UiUtilsTest {

    @Test
    public void isFromToday_now() {

        final ZonedDateTime zonedDateTime = ZonedDateTime.now();

        assertThat(UiUtils.isFromToday(zonedDateTime)).isTrue();
    }

    @Test
    public void isFromToday_yesterdays_night() {

        final ZonedDateTime zonedDatetime = DateUtils.toSystemDefault(DateUtils.now())
                .minusDays(1)
                .withHour(23)
                .withMinute(59)
                .withSecond(59);

        assertThat(UiUtils.isFromToday(zonedDatetime)).isFalse();
    }

    @Test
    public void isFromToday_todays_early_morning() {

        final ZonedDateTime zonedDateTime = DateUtils.toSystemDefault(DateUtils.now())
                .withHour(0)
                .withMinute(0)
                .withSecond(0);

        assertThat(UiUtils.isFromToday(zonedDateTime)).isTrue();
    }

    @Test
    public void isFromThisYear_now() {

        final ZonedDateTime zonedDateTime = ZonedDateTime.now();

        assertThat(UiUtils.isFromThisYear(zonedDateTime)).isTrue();
    }

    @Test
    public void isFromThisYear_this_years_first_day() {

        final ZonedDateTime zonedDateTime = DateUtils.toSystemDefault(DateUtils.now())
                .withDayOfYear(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0);

        assertThat(UiUtils.isFromThisYear(zonedDateTime)).isTrue();
    }

    @Test
    public void isFromThisYear_last_years_last_day() {

        final ZonedDateTime zonedDateTime = DateUtils.toSystemDefault(DateUtils.now())
                .minusYears(1)
                .withMonth(12)
                .withDayOfMonth(31)
                .withHour(23)
                .withMinute(59)
                .withSecond(59);

        assertThat(UiUtils.isFromThisYear(zonedDateTime)).isFalse();
    }
}
