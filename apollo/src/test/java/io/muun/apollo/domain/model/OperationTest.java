package io.muun.apollo.domain.model;

import io.muun.apollo.BaseTest;
import io.muun.common.model.OperationStatus;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import org.junit.Test;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class OperationTest extends BaseTest {

    @Test
    public void getBalanceInSatoshisForUser_outgoing_tx() {

        final Operation operation = Fixture.from(Operation.class).gimme("outgoing internal");

        assertThat(operation.getBalanceInSatoshisForUser())
                .isEqualTo(- operation.amount.inSatoshis - operation.fee.inSatoshis);
    }

    @Test
    public void getBalanceInSatoshisForUser_incoming_tx() {

        final Operation operation = Fixture.from(Operation.class).gimme("incoming internal");

        assertThat(operation.getBalanceInSatoshisForUser())
                .isEqualTo(operation.amount.inSatoshis);
    }

    @Test
    public void isFailed_successful_tx() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid");

        operation.status = OperationStatus.CREATED;
        assertThat(operation.isFailed()).isFalse();

        operation.status = OperationStatus.SIGNING;
        assertThat(operation.isFailed()).isFalse();

        operation.status = OperationStatus.SIGNED;
        assertThat(operation.isFailed()).isFalse();

        operation.status = OperationStatus.BROADCASTED;
        assertThat(operation.isFailed()).isFalse();

        operation.status = OperationStatus.CONFIRMED;
        assertThat(operation.isFailed()).isFalse();

        operation.status = OperationStatus.SETTLED;
        assertThat(operation.isFailed()).isFalse();
    }

    @Test
    public void isFailed_failed_tx() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid");

        operation.status = OperationStatus.DROPPED;
        assertThat(operation.isFailed()).isTrue();

        operation.status = OperationStatus.FAILED;
        assertThat(operation.isFailed()).isTrue();
    }

    @Test
    public void isFromToday_now() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid", new Rule() {{
            add("creationDate", ZonedDateTime.now());
        }});

        assertThat(operation.isFromToday()).isTrue();
    }

    @Test
    public void isFromToday_yesterdays_night() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid", new Rule() {{
            add("creationDate", ZonedDateTime.now()
                    .minusDays(1)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .withHour(23)
                    .withMinute(59)
                    .withSecond(59)
            );
        }});

        assertThat(operation.isFromToday()).isFalse();
    }

    @Test
    public void isFromToday_todays_early_morning() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid", new Rule() {{
            add("creationDate", ZonedDateTime.now()
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
            );
        }});

        assertThat(operation.isFromToday()).isTrue();
    }

    @Test
    public void isFromThisYear_now() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid", new Rule() {{
            add("creationDate", ZonedDateTime.now());
        }});

        assertThat(operation.isFromThisYear()).isTrue();
    }

    @Test
    public void isFromThisYear_this_years_first_day() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid", new Rule() {{
            add("creationDate", ZonedDateTime.now()
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .withDayOfYear(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
            );
        }});

        assertThat(operation.isFromThisYear()).isTrue();
    }

    @Test
    public void isFromThisYear_last_years_last_day() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid", new Rule() {{
            add("creationDate", ZonedDateTime.now()
                    .minusYears(1)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .withMonth(12)
                    .withDayOfMonth(31)
                    .withHour(23)
                    .withMinute(59)
                    .withSecond(59)
            );
        }});

        assertThat(operation.isFromThisYear()).isFalse();
    }
}