#include <unistd.h>
#include "highlight.h"

extern void highlight_fn(std::vector<char> const& text, std::function<bool()> const& isCanceled, Color* out);

static bool isCanceled() {
  return false;
}

int main(int argc, char* argv[]) {
  const int bufsz = 128;
  char in[bufsz];
  Color out[bufsz];

  int inlen;
  while (0 < (inlen = read(STDIN_FILENO, in, sizeof(in)))) {
    std::vector<char> inv(in, in + inlen);
    highlight_fn(inv, isCanceled, out);
    for (int i = 0; i < inlen; ++i) {
      Color& c = out[i];
      uint8_t outbuf[] = { c.r, c.g, c.b };
      write(STDOUT_FILENO, outbuf, sizeof(outbuf));
    }
  }
  return 0;
}
