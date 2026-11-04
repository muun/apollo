package data

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/muun/libwallet/base/data/houston/model"
)

// Cached payloads outlive the build that wrote them, and json.Unmarshal
// decodes a stale payload into a changed DTO "successfully", zeroing what's
// missing. cacheSchemaVersion exists to invalidate those, but bumping it
// relies on noticing the DTO change. These goldens pin the serialized shape
// of everything the cache stores: if one fails, the cached wire format
// changed — bump cacheSchemaVersion and update the golden here.
const bumpReminder = "cached wire format changed: " +
	"bump cacheSchemaVersion and update this golden"

func TestCachedCardsShapeIsPinned(t *testing.T) {
	cards := model.SecurityCardsMarketplaceCardsJSON{
		Items: []model.MarketplaceCardJSON{
			{
				UUID:          "card-uuid",
				ProviderUUID:  "provider-uuid",
				SKU:           "SKU-01",
				ImageURL:      "https://example.com/card.png",
				Material:      "METAL",
				WidthMm:       86.5,
				HeightMm:      54,
				ThicknessMm:   0.8,
				WeightGrams:   5.5,
				SecureElement: "EAL_6_PLUS",
				HasStock:      true,
			},
		},
	}

	got, err := json.Marshal(cards)
	require.NoError(t, err)
	require.JSONEq(t, `{
		"items": [{
			"uuid": "card-uuid",
			"providerUuid": "provider-uuid",
			"sku": "SKU-01",
			"imageUrl": "https://example.com/card.png",
			"material": "METAL",
			"widthMm": 86.5,
			"heightMm": 54,
			"thicknessMm": 0.8,
			"weightGrams": 5.5,
			"secureElement": "EAL_6_PLUS",
			"hasStock": true
		}]
	}`, string(got), bumpReminder)
}

func TestCachedPricesShapeIsPinned(t *testing.T) {
	prices := model.SecurityCardsMarketplacePricesJSON{
		Items: []model.MarketplaceProviderPricesJSON{
			{
				ProviderUUID: "provider-uuid",
				CardPrice: model.MarketplaceAmountJSON{
					CurrencyCode: "EUR", Amount: "200.00",
				},
				ShippingPrices: []model.MarketplaceShippingPriceJSON{
					{
						Price: model.MarketplaceAmountJSON{
							CurrencyCode: "EUR", Amount: "15.00",
						},
						CountryCodes: []string{"US", "DE"},
					},
				},
				MinShippingTimeDays: 5,
				MaxShippingTimeDays: 10,
			},
		},
	}

	got, err := json.Marshal(prices)
	require.NoError(t, err)
	require.JSONEq(t, `{
		"items": [{
			"providerUuid": "provider-uuid",
			"cardPrice": {"currencyCode": "EUR", "amount": "200.00"},
			"shippingPrices": [{
				"price": {"currencyCode": "EUR", "amount": "15.00"},
				"countryCodes": ["US", "DE"]
			}],
			"minShippingTimeDays": 5,
			"maxShippingTimeDays": 10
		}]
	}`, string(got), bumpReminder)
}

func TestCacheEnvelopeShapeIsPinned(t *testing.T) {
	envelope := cacheEnvelope{
		SchemaVersion:       cacheSchemaVersion,
		FetchedAtUnixMillis: 1700000000000,
		Payload:             json.RawMessage(`{"items":[]}`),
	}

	got, err := json.Marshal(envelope)
	require.NoError(t, err)
	require.JSONEq(t, `{
		"schemaVersion": 1,
		"fetchedAtUnixMillis": 1700000000000,
		"payload": {"items": []}
	}`, string(got), bumpReminder)
}
