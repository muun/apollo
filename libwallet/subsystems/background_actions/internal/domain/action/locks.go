package action

import (
	"math"
	"time"

	"github.com/muun/libwallet/platform/concurrency/memlock"
)

var (
	wakeLock = memlock.NewLockSpec("backgroundActionsWake", 200*time.Millisecond)
	runLock  = memlock.NewLockSpec(
		"backgroundActionsRun",
		time.Duration(math.MaxInt64), // unbounded
	)
)
