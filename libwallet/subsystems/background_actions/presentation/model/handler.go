package model

import "context"

// Handler is the code a function runs. It returns nil when done.
// ctx is cancelled at the handler's deadline.
type Handler func(ctx context.Context) error
