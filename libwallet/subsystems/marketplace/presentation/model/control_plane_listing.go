package model

// ControlPlaneListing whitelists one provider's shipping countries.
type ControlPlaneListing struct {
	ProviderUUID string
	Countries    []string
}

func NewControlPlaneListing(
	providerUUID string,
	countries []string,
) ControlPlaneListing {
	return ControlPlaneListing{
		ProviderUUID: providerUUID,
		Countries:    countries,
	}
}
