package service

import (
	"encoding/json"
	"github.com/muun/libwallet/service/model"
	"strconv"
	"testing"
)

const houstonUrl = "http://localhost:8080"

func TestInvalidEndpoint_Integration(t *testing.T) {
	provider := TestProvider{
		ClientVersion:     "1302",
		ClientVersionName: "53.3",
		Language:          "en",
		ClientType:        "APOLLO",
		BaseURL:           houstonUrl,
	}
	houstonService := NewHoustonService(&provider)
	r := request[any]{
		Method: MethodGet,
		Path:   "/invalid-endpoint",
	}
	_, err := r.do(&houstonService.client)
	if err == nil {
		t.Fatal("expected error")
	}

	devError := HoustonResponseError{}
	err = json.Unmarshal([]byte(err.Error()), &devError)
	if err != nil {
		t.Fatal(err)
	}
	if devError.RequestId == 0 {
		t.Fatal("RequestId should not be zero")
	}
	if devError.ErrorCode != 404 {
		t.Fatalf("want %v, but got %v", 404, devError.ErrorCode)
	}
	if devError.Status != 400 {
		t.Fatalf("want %v, but got %v", 400, devError.Status)
	}
	want := "Not found"
	if devError.Message != want {
		t.Fatalf("want %v, but got %v", want, devError.Message)
	}
	want = "HTTP 404 Not Found"
	if devError.DeveloperMessage != want {
		t.Fatalf("want %v, but got %v", want, devError.Message)
	}
}

func TestValidEndpointButNoAuthToken_Integration(t *testing.T) {
	provider := TestProvider{
		ClientVersion:     "1302",
		ClientVersionName: "53.3",
		Language:          "en",
		ClientType:        "APOLLO",
		BaseURL:           houstonUrl,
	}
	houstonService := NewHoustonService(&provider)
	r := request[any]{
		Method: MethodGet,
		Path:   "/user",
	}
	_, err := r.do(&houstonService.client)
	if err == nil {
		t.Fatal("expected error")
	}

	devError := HoustonResponseError{}
	err = json.Unmarshal([]byte(err.Error()), &devError)
	if err != nil {
		t.Fatal(err)
	}
	if devError.RequestId == 0 {
		t.Fatal("RequestId should not be zero")
	}
	if devError.ErrorCode != 2016 {
		t.Fatalf("want %v, but got %v", 2016, devError.ErrorCode)
	}
	if devError.Status != 400 {
		t.Fatalf("want %v, but got %v", 400, devError.Status)
	}
	want := "Not authorized"
	if devError.Message != want {
		t.Fatalf("want %v, but got %v", want, devError.Message)
	}
	want = "Missing or invalid auth token"
	if devError.DeveloperMessage != want {
		t.Fatalf("want %v, but got %v", want, devError.Message)
	}
}

func TestValidEndpointButInvalidAuthToken_Integration(t *testing.T) {
	provider := TestProvider{
		ClientVersion:     "1302",
		ClientVersionName: "53.3",
		Language:          "en",
		ClientType:        "APOLLO",
		AuthToken:         "invalid-auth-token",
		BaseURL:           houstonUrl,
	}
	houstonService := NewHoustonService(&provider)

	// Test valid endpoint that requires AuthToken
	_, err := houstonService.FetchFeeWindow()
	if err == nil {
		t.Fatal("expected error")
	}

	houstonError := HoustonResponseError{}
	err = json.Unmarshal([]byte(err.Error()), &houstonError)
	if err != nil {
		t.Fatal(err)
	}
	if houstonError.RequestId == 0 {
		t.Fatal("RequestId should not be zero")
	}
	if houstonError.ErrorCode != 2016 {
		t.Fatalf("want %v, but got %v", 2016, houstonError.ErrorCode)
	}
	if houstonError.Status != 400 {
		t.Fatalf("want %v, but got %v", 400, houstonError.Status)
	}
	want := "Not authorized"
	if houstonError.Message != want {
		t.Fatalf("want %v, but got %v", want, houstonError.Message)
	}
	want = "Missing or invalid auth token"
	if houstonError.DeveloperMessage != want {
		t.Fatalf("want %v, but got %v", want, houstonError.Message)
	}
}

func TestValidEndpointAndValidAuthToken_Integration(t *testing.T) {
	provider := TestProvider{
		ClientVersion:     "1205",
		ClientVersionName: "2.9.2",
		Language:          "en",
		ClientType:        "FALCON",
		BaseURL:           houstonUrl,
	}
	strClientVersion, err := strconv.Atoi(provider.ClientVersion)
	if err != nil {
		t.Fatal(err)
	}

	houstonService := NewHoustonService(&provider)

	// Create first session to get and set a valid AuthToken
	sessionJson := model.CreateFirstSessionJson{
		Client: model.ClientJson{
			Type:        provider.ClientType,
			BuildType:   "debug",
			Version:     strClientVersion,
			VersionName: provider.ClientVersionName,
			Language:    provider.Language,
		},
		GcmToken:        nil,
		PrimaryCurrency: "USD",
		BasePublicKey: model.PublicKeyJson{
			Key:  "tpubDAygaiK3eZ9hpC3aQkxtu5fGSTK4P7QKTwwGExN8hGZytjpEfsrUjtM8ics8Y7YLrvf1GLBZTFjcpmkEP1KKTRyo8D2ku5zz49bRudDrngd",
			Path: "m/schema:1'/recovery:1'",
		},
	}
	sessionOkJson, err := houstonService.CreateFirstSession(sessionJson)
	if err != nil {
		t.Fatal(err)
	}
	if sessionOkJson.CosigningPublicKey.Key == "" {
		t.Fatal("Cosigning public key should not be empty")
	}
	if sessionOkJson.CosigningPublicKey.Path == "" {
		t.Fatal("Cosigning public key path should not be empty")
	}
	if sessionOkJson.SwapServerPublicKey.Key == "" {
		t.Fatal("Swap server public key should not be empty")
	}
	if sessionOkJson.SwapServerPublicKey.Path == "" {
		t.Fatal("Swap server public key path should not be empty")
	}

	// Test valid endpoint that requires AuthToken
	feeWindow, err := houstonService.FetchFeeWindow()
	if err != nil {
		t.Fatal(err)
	}

	if feeWindow.Id <= 0 {
		t.Fatal("Fee window id should be greater than zero")
	}
	if feeWindow.FetchDate == "" {
		t.Fatal("FetchDate should not be empty")
	}
	if feeWindow.TargetedFees == nil {
		t.Fatal("TargetedFees should not be nil")
	}
	if feeWindow.FastConfTarget <= 0 {
		t.Fatal("FastConfTarget should be greater than zero")
	}
	if feeWindow.MediumConfTarget <= 0 {
		t.Fatal("MediumConfTarget should be greater than zero")
	}
	if feeWindow.SlowConfTarget <= 0 {
		t.Fatal("SlowConfTarget should be greater than zero")
	}
}
