FROM rust:1.84

ARG RUSTUP_TOOLCHAIN=nightly-2024-12-16

# install toolchain
RUN rustup toolchain install ${RUSTUP_TOOLCHAIN} \
    && rustup override set ${RUSTUP_TOOLCHAIN} \
    && rustup component add clippy \
    && rustup component add rustfmt
