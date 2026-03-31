FROM rust:1.84@sha256:738ae99a3d75623f41e6882566b4ef37e38a9840244a47efd4a0ca22e9628b88

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
