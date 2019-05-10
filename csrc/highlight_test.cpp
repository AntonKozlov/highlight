#include "highlight.h"

static int state;

static Color grey{128, 128, 128};
static Color black{0,0,0};

void highlight_fn(std::vector<char> const& text, std::function<bool()> const& isCanceled, Color* out) {
  for (char c : text) {

    switch (c) {
    case 'T':
      state = 1;
      *out++ = grey;
      break;
    case 'F':
      state = 0;
      *out++ = grey;
      break;

    case 'S':
      if (state == 1) {
	    using namespace std::chrono_literals;
	    std::this_thread::sleep_for(1s);
      }
      *out++ = grey;
      break;

    case 'C':
      if (state == 1) {
	    std::terminate();
      }
      *out++ = grey;
      break;

    case 'R':
      *out++ = Color{255, 0, 0};
      break;
    case 'G':
      *out++ = Color{0, 255, 0};
      break;
    case 'B':
      *out++ = Color{0, 0, 255};
      break;

    default:
      if (std::isdigit(c))
	    *out++ = Color{0, 0, 255};
      else if (std::isspace(c))
	    *out++ = Color{255, 255, 255};
      else
	    *out++ = Color{0, 0, 0};
    }
  }
}
