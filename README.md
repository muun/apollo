![muun](https://muun.com/images/github-banner-v2.png)

## About

This is the source code repository for muun's android wallet. Muun is a non-custodial 2-of-2 multisig wallet with a special focus on security and ease of use.

## Structure

The app follows the [clean](https://fernandocejas.com/2014/09/03/architecting-android-the-clean-way/) [architecture](https://fernandocejas.com/2015/07/18/architecting-android-the-evolution/) pattern, and has three layers:

* **Data:** handles the data backends, such as the database, the operating system, or the network.
* **Domain:** contains the models and business logic (use cases in clean architecture lingo).
* **Presentation:** contains the UI code.

There's also a pure java **common** module with code shared all over.

## Build

For instructions on how to build Muun Wallet please refer to BUILD.md

## Auditing

* Most of the key handling and transaction crafting operations happen in the **common** module.
* All the keystore and data handling happens in the **data** layer.
* All the business logic that decides when to sign what happens in the **domain** layer.
* The **presentation** layer only depends on the **domain** layer, it never references **data** directly.

## Responsible Disclosure

Send us an email to report any security related bugs or vulnerabilities at [security@muun.com](mailto:security@muun.com).

You can encrypt your email message using our public PGP key.

Public key fingerprint: `1299 28C1 E79F E011 6DA4 C80F 8DB7 FD0F 61E6 ED76`
