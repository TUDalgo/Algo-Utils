package org.tudalgo.algoutils.tutor.general.assertions.basic;

import org.tudalgo.algoutils.tutor.general.Environment;
import org.tudalgo.algoutils.tutor.general.assertions.ResultOfFail;
import org.tudalgo.algoutils.tutor.general.assertions.actual.NoActual;
import org.tudalgo.algoutils.tutor.general.assertions.expected.Nothing;

/**
 * <p>A basic implementation of a result of an always failing test.</p>
 *
 * @author Dustin Glaser
 */
public class BasicResultOfFail extends BasicResult<BasicResultOfFail, NoActual, BasicFail, Nothing> implements ResultOfFail<BasicResultOfFail, BasicFail> {

    /**
     * <p>Constructs a new result with the given environment, test and exception.</p>
     *
     * @param test the test
     */
    public BasicResultOfFail(Environment environment, BasicFail test, Exception exception) {
        super(environment, test, null, exception, false);
    }

    public static class Builder extends BasicResult.Builder<BasicResultOfFail, NoActual, BasicFail, Nothing, Builder> implements ResultOfFail.Builder<BasicResultOfFail, BasicFail, Builder> {

        public Builder(Environment environment) {
            super(environment);
        }

        @Override
        public BasicResultOfFail build() {
            return new BasicResultOfFail(environment, test, exception);
        }

        public static class Factory extends BasicResult.Builder.Factory<BasicResultOfFail, NoActual, BasicFail, Nothing, Builder> implements ResultOfFail.Builder.Factory<BasicResultOfFail, BasicFail, Builder> {

            public Factory(Environment environment) {
                super(environment);
            }

            @Override
            public Builder builder() {
                return new Builder(environment);
            }
        }
    }
}
