package encryption

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"math"
	"math/big"
)

func PaddedSerializeBigInt(size uint, x *big.Int) []byte {
	src := x.Bytes()
	dst := make([]byte, 0, size)

	for i := 0; i < int(size)-len(src); i++ {
		dst = append(dst, 0)
	}

	return append(dst, src...)
}

func addVariableBytes(writer io.Writer, data []byte) error {
	if len(data) > math.MaxUint16 {
		return fmt.Errorf("data length can't exceeed %v", math.MaxUint16)
	}

	dataLen := uint16(len(data))
	err := binary.Write(writer, binary.BigEndian, &dataLen)
	if err != nil {
		return fmt.Errorf("failed to write var bytes len: %w", err)
	}

	n, err := writer.Write(data)
	if err != nil || n != len(data) {
		return errors.New("failed to write var bytes")
	}

	return nil
}

func extractVariableBytes(reader *bytes.Reader, limit int) ([]byte, error) {
	var len uint16
	err := binary.Read(reader, binary.BigEndian, &len)
	if err != nil || int(len) > limit || int(len) > reader.Len() {
		return nil, errors.New("failed to read byte array len")
	}

	result := make([]byte, len)
	n, err := reader.Read(result)
	if err != nil || n != int(len) {
		return nil, errors.New("failed to extract byte array")
	}

	return result, nil
}

func extractVariableString(reader *bytes.Reader, limit int) (string, error) {
	bytes, err := extractVariableBytes(reader, limit)
	return string(bytes), err
}
