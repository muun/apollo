package io.muun.apollo.domain.model;

import io.muun.apollo.BaseTest;
import io.muun.common.model.OperationStatus;

import br.com.six2six.fixturefactory.Fixture;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OperationTest extends BaseTest {

    @Test
    @Ignore
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
    @Ignore
    public void isFailed_failed_tx() {

        final Operation operation = Fixture.from(Operation.class).gimme("valid");

        operation.status = OperationStatus.DROPPED;
        assertThat(operation.isFailed()).isTrue();

        operation.status = OperationStatus.FAILED;
        assertThat(operation.isFailed()).isTrue();
    }
}