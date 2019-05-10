#include "highlight.h"

void highlight_fn(std::vector<char> const& text, std::function<bool()> const& isCanceled, Color* out) {
  highlight(text, isCanceled, out);
}
