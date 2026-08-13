package presentation

import (
	"fmt"
	"os"
	"testing"

	"github.com/muun/libwallet/presentation/api"
)

func TestGenerateEmergencyKitPDFGrpc(t *testing.T) {
	ekInput := api.EKInputRequest_builder{
		FirstEncryptedKey:  "5zZPjShrCywaeaqK3bPxL9bG18eLcXwQ5DyAkVy8asPujTWK58PJFyjwixASB967rfQcG2PhnZJ6ksKVWasup29WmPtAyjN6heNYC7pQARUxMsVrUVD5pGc4aJH5W3QdXDFhyiRrszFsedz2T4s", //nolint:lll
		SecondEncryptedKey: "4UrzWNdJzNg5XYkypVCAqxLreHnK6uYyaUNTmuEkdet6T1dDhHKkCicTT7MKa2BCKA4TA39o4gAzjBCageg9bvRVZs2deazEykpTgPaY6yF25AK1ckdT1dVKE9NbmVfuf5N6qFVLRBe1myYS6eD", //nolint:lll
		FirstFingerprint:   "af932357",
		SecondFingerprint:  "61f4d2a0",
		RcChecksum:         "checksum123",
	}.Build()

	t.Run("Basic (Eng)", func(t *testing.T) {
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		outputPath := fmt.Sprintf("%s/emergency_kit_en.pdf", os.TempDir())

		request := api.GenerateEmergencyKitPDFRequest_builder{
			EkInput:    ekInput,
			OutputPath: outputPath,
			Language:   "en",
		}.Build()

		result, err := client.GenerateEmergencyKitPDF(ctx, request)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		if result.GetVerificationCode() != "429645" {
			t.Fatalf("Verification Code should be 429645, its to supposed to be deterministic")
		}
		if result.GetVersion() != 3 {
			t.Fatalf("Version should be 3")
		}

		profiling := result.GetProfiling()
		if profiling == nil {
			t.Fatal("expected render profiling in the response")
		}
		// registerImages and totalInsideGo are reliably multi-ms; smaller stages (incl. embed) can
		// truncate to 0ms. Allocs stable.
		if profiling.GetRegisterImagesMs() == 0 ||
			profiling.GetTotalHeapAllocatedBytes() == 0 ||
			profiling.GetTotalInsideGoMs() == 0 {
			t.Fatalf("expected non-zero profiling stats, got %+v", profiling)
		}

		if _, err := os.Stat(outputPath); os.IsNotExist(err) {
			t.Fatalf("PDF file not created at expected path: %s", outputPath)
		}

		_ = os.Remove(outputPath)
	})

	t.Run("iOS FileURL handling (esp)", func(t *testing.T) {
		conn, ctx := newGrpcClient(t)
		defer conn.Close()
		client := api.NewWalletServiceClient(conn)

		fileURLPath := fmt.Sprintf("file://%s/emergency_kit_es.pdf", os.TempDir())

		request := api.GenerateEmergencyKitPDFRequest_builder{
			EkInput:    ekInput,
			OutputPath: fileURLPath,
			Language:   "es",
		}.Build()

		_, err := client.GenerateEmergencyKitPDF(ctx, request)
		if err != nil {
			failWithGrpcErrorDetails(t, err)
		}

		expectedPath := fmt.Sprintf("%s/emergency_kit_es.pdf", os.TempDir())
		if _, err := os.Stat(expectedPath); os.IsNotExist(err) {
			t.Fatalf("PDF file not created at expected path: %s", expectedPath)
		}

		_ = os.Remove(expectedPath)
	})
}
