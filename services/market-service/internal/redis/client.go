package redis

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
	"time"
)

type Client struct {
	addr     string
	password string
	db       int
	timeout  time.Duration
}

type CommandError struct {
	Message string
}

func (e CommandError) Error() string {
	return e.Message
}

func NewClient(addr string, password string, db int) *Client {
	if addr == "" {
		addr = "localhost:6379"
	}
	return &Client{
		addr:     addr,
		password: password,
		db:       db,
		timeout:  15 * time.Second,
	}
}

func (c *Client) Do(ctx context.Context, args ...any) (any, error) {
	if len(args) == 0 {
		return nil, errors.New("redis command is empty")
	}

	conn, err := c.dial(ctx)
	if err != nil {
		return nil, err
	}
	defer conn.Close()

	deadline := time.Now().Add(c.timeout)
	if ctxDeadline, ok := ctx.Deadline(); ok && ctxDeadline.Before(deadline) {
		deadline = ctxDeadline
	}
	_ = conn.SetDeadline(deadline)

	reader := bufio.NewReader(conn)
	writer := bufio.NewWriter(conn)

	if c.password != "" {
		if err := writeCommand(writer, "AUTH", c.password); err != nil {
			return nil, err
		}
		if _, err := readValue(reader); err != nil {
			return nil, err
		}
	}

	if c.db > 0 {
		if err := writeCommand(writer, "SELECT", c.db); err != nil {
			return nil, err
		}
		if _, err := readValue(reader); err != nil {
			return nil, err
		}
	}

	if err := writeCommand(writer, args...); err != nil {
		return nil, err
	}

	return readValue(reader)
}

func (c *Client) dial(ctx context.Context) (net.Conn, error) {
	dialer := net.Dialer{Timeout: 5 * time.Second}
	return dialer.DialContext(ctx, "tcp", c.addr)
}

func writeCommand(writer *bufio.Writer, args ...any) error {
	if _, err := fmt.Fprintf(writer, "*%d\r\n", len(args)); err != nil {
		return err
	}

	for _, arg := range args {
		data := commandArg(arg)
		if _, err := fmt.Fprintf(writer, "$%d\r\n", len(data)); err != nil {
			return err
		}
		if _, err := writer.Write(data); err != nil {
			return err
		}
		if _, err := writer.WriteString("\r\n"); err != nil {
			return err
		}
	}

	return writer.Flush()
}

func commandArg(arg any) []byte {
	switch value := arg.(type) {
	case []byte:
		return value
	case string:
		return []byte(value)
	case int:
		return []byte(strconv.Itoa(value))
	case int64:
		return []byte(strconv.FormatInt(value, 10))
	case uint64:
		return []byte(strconv.FormatUint(value, 10))
	case time.Duration:
		return []byte(strconv.FormatInt(value.Milliseconds(), 10))
	default:
		return []byte(fmt.Sprint(value))
	}
}

func readValue(reader *bufio.Reader) (any, error) {
	prefix, err := reader.ReadByte()
	if err != nil {
		return nil, err
	}

	switch prefix {
	case '+':
		return readLine(reader)
	case '-':
		line, err := readLine(reader)
		if err != nil {
			return nil, err
		}
		return nil, CommandError{Message: line}
	case ':':
		line, err := readLine(reader)
		if err != nil {
			return nil, err
		}
		return strconv.ParseInt(line, 10, 64)
	case '$':
		line, err := readLine(reader)
		if err != nil {
			return nil, err
		}
		n, err := strconv.Atoi(line)
		if err != nil {
			return nil, err
		}
		if n < 0 {
			return nil, nil
		}
		data := make([]byte, n+2)
		if _, err := io.ReadFull(reader, data); err != nil {
			return nil, err
		}
		return string(data[:n]), nil
	case '*':
		line, err := readLine(reader)
		if err != nil {
			return nil, err
		}
		n, err := strconv.Atoi(line)
		if err != nil {
			return nil, err
		}
		if n < 0 {
			return nil, nil
		}
		items := make([]any, 0, n)
		for i := 0; i < n; i++ {
			item, err := readValue(reader)
			if err != nil {
				return nil, err
			}
			items = append(items, item)
		}
		return items, nil
	default:
		return nil, fmt.Errorf("redis protocol error: unexpected prefix %q", prefix)
	}
}

func readLine(reader *bufio.Reader) (string, error) {
	line, err := reader.ReadString('\n')
	if err != nil {
		return "", err
	}
	return strings.TrimSuffix(strings.TrimSuffix(line, "\n"), "\r"), nil
}

func isBusyGroup(err error) bool {
	var commandErr CommandError
	if errors.As(err, &commandErr) {
		return strings.Contains(commandErr.Message, "BUSYGROUP")
	}
	return false
}

func stringValue(value any) (string, bool) {
	switch typed := value.(type) {
	case string:
		return typed, true
	case int64:
		return strconv.FormatInt(typed, 10), true
	default:
		return "", false
	}
}
