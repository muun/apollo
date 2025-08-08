#include <stdint.h>

typedef struct CharArray
{
    char *data;
    uint64_t len;
} CharArray;

CharArray plonky2_server_key_verify(CharArray, CharArray, CharArray, CharArray, CharArray);
