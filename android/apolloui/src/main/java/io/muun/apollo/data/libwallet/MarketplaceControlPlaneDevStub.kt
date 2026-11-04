package io.muun.apollo.data.libwallet

import rpc.WalletServiceOuterClass.SCMControlPlaneListing
import rpc.WalletServiceOuterClass.SCMSaveControlPlaneRequest

/**
 * The marketplace control plane the app pushes into libwallet on every
 * RealTimeData sync.
 *
 * TODO: swap this stub for the levers in the RealTimeData response once
 * houston serves them. The content mirrors libwallet's mock houston
 * fixtures so the pushed data lines up with what the mock serves, but it is
 * free development data: the contract is the proto, not this stub.
 */
object MarketplaceControlPlaneDevStub {

    @JvmField
    val request: SCMSaveControlPlaneRequest =
        SCMSaveControlPlaneRequest.newBuilder()
            .addAllCountries(
                listOf("US", "DE", "ES", "GB", "BR", "FR", "CA", "MX", "IT", "NL")
            )
            .addListings(
                listing(
                    "4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14",
                    listOf("US", "DE", "ES", "GB", "BR")
                )
            )
            .addListings(
                listing(
                    "9e2c6b4a-1f8d-473e-b9a5-c3d2e1f4a7b8",
                    listOf("US", "FR", "CA", "MX")
                )
            )
            .addListings(
                listing(
                    "b3f5c1d8-4a72-4e9b-8c61-d3a9e2f4b7c0",
                    listOf("DE", "IT", "NL")
                )
            )
            .addAllCards(
                listOf(
                    "a1b2c3d4-1111-4000-8000-000000000001",
                    "a1b2c3d4-1111-4000-8000-000000000002",
                    "a1b2c3d4-1111-4000-8000-000000000003",
                    "a1b2c3d4-1111-4000-8000-000000000004",
                    "b2c3d4e5-2222-4000-8000-000000000001",
                    "b2c3d4e5-2222-4000-8000-000000000002",
                    "b2c3d4e5-2222-4000-8000-000000000003",
                    "b2c3d4e5-2222-4000-8000-000000000004",
                    "b2c3d4e5-2222-4000-8000-000000000005",
                    "b2c3d4e5-2222-4000-8000-000000000006",
                    "b2c3d4e5-2222-4000-8000-000000000007",
                    "b2c3d4e5-2222-4000-8000-000000000008",
                    "b2c3d4e5-2222-4000-8000-000000000009",
                    "c3d4e5f6-3333-4000-8000-000000000001",
                    "c3d4e5f6-3333-4000-8000-000000000002"
                )
            )
            .build()

    private fun listing(
        providerUuid: String,
        countries: List<String>,
    ): SCMControlPlaneListing {
        return SCMControlPlaneListing.newBuilder()
            .setProviderUuid(providerUuid)
            .addAllCountries(countries)
            .build()
    }
}
