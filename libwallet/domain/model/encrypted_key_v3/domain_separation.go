package encrypted_key_v3

const (
	userFirstHalfToRecoveryCode  = "muun.com/cosigning-key/1/1/recovery-code"
	userSecondHalfToRecoveryCode = "muun.com/cosigning-key/1/2/recovery-code"
	muunFirstHalfToRecoveryCode  = "muun.com/cosigning-key/2/1/recovery-code"
	muunSecondHalfToRecoveryCode = "muun.com/cosigning-key/2/2/recovery-code"
	MuunFirstHalfToClient        = "muun.com/cosigning-key/2/1/client"
)

type keyBearer uint8

const (
	user keyBearer = iota + 1
	muun
)
