package bad

type helper struct{} // want `the file's namesake struct LateNamesake must be defined before helper`

type LateNamesake struct{}
