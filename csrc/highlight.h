#pragma once

#include <vector>
#include <functional>
#include <cstdint>
#include <cctype>
#include <random>
#include <thread>
#include <exception>

struct Color {
  std::uint8_t r, g, b;
};

template<typename OutIter>
void highlight(std::vector<char> const& text, std::function<bool()> const& isCanceled, OutIter out) {
  if (isCanceled()) return;
  std::srand(std::time(0));


  if (std::rand() % 10 == 0) {
    using namespace std::chrono_literals;
    if (isCanceled()) return;
    std::this_thread::sleep_for(5s);
  }

  for (char c : text) {
    if (isCanceled()) return;


    if (std::rand() % 30000 == 0) {
      std::terminate();
    }

    if (std::isdigit(c))
      *out++ = Color{0, 0, 255};
    else if (std::isspace(c))
      *out++ = Color{255, 255, 255};
    else
      *out++ = Color{0, 0, 0};
  }
}
