FROM rust:1.84

ARG RUSTUP_TOOLCHAIN=nightly-2024-12-16

# Install toolchain
RUN rustup toolchain install ${RUSTUP_TOOLCHAIN} \
    && rustup override set ${RUSTUP_TOOLCHAIN} \
    && rustup component add clippy \
    && rustup component add rustfmt

# Install targets
# Use a different step so that docker can reuse the previous layer if only targets change
ARG TARGETS
RUN rustup target add --toolchain ${RUSTUP_TOOLCHAIN} $TARGETS \
