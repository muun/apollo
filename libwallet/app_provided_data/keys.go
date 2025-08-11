package app_provided_data

type KeyData struct {
	Serialized string
	Path       string
}

type KeyProvider interface {
	FetchUserKey() (*KeyData, error)
	FetchMuunKey() (*KeyData, error)
	FetchMaxDerivedIndex() int
}
