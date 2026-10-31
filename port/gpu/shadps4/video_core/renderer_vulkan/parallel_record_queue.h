// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <array>
#include <cassert>
#include <condition_variable>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>
#include <utility>
#include <vector>

namespace Vulkan {

// Two independent recording lanes, one command pool per lane. Only the producer
// starts/reuses batches, after Sync. Host side effects in lane 1 use AfterPrefix:
// recording Vulkan commands may run ahead, publishing guest data may not. A
// deferred host task never stalls the rest of lane 1's Vulkan recording.
template <class Chunk, class Buffer>
class ParallelRecordQueue {
public:
    using Begin = std::function<Buffer(unsigned)>;
    ParallelRecordQueue(Begin begin, std::function<void(unsigned)> enter)
        : begin{std::move(begin)} {
        for (unsigned i = 0; i < 2; ++i) {
            threads[i] = std::jthread([this, i, enter](std::stop_token stop) {
                enter(i);
                Run(i, stop);
            });
        }
    }
    ~ParallelRecordQueue() {
        Sync();
        for (auto& thread : threads) thread.request_stop();
        for (auto& wake : work_cv) wake.notify_all();
        for (auto& thread : threads) thread.join();
    }
    ParallelRecordQueue(const ParallelRecordQueue&) = delete;
    ParallelRecordQueue& operator=(const ParallelRecordQueue&) = delete;

    void Start(Buffer first) {
        std::scoped_lock lock{mutex};
        assert(Idle());
        buffers = {first, Buffer{}};
        prefix_complete = false;
        deferred_host = 0;
    }
    std::unique_ptr<Chunk> Acquire() {
        std::scoped_lock lock{mutex};
        if (free.empty()) return std::make_unique<Chunk>();
        auto chunk = std::move(free.back());
        free.pop_back();
        return chunk;
    }
    void Push(unsigned lane, std::unique_ptr<Chunk> chunk) {
        bool wake;
        {
            std::scoped_lock lock{mutex};
            queues[lane].push_back(std::move(chunk));
            wake = sleeping[lane];
        }
        if (wake) work_cv[lane].notify_one();
    }
    void CompletePrefix() {
        bool drain;
        {
            std::scoped_lock lock{mutex};
            prefix_complete = true;
            drain = !ordered_running && !ordered.empty();
            if (drain) ordered_running = true;
        }
        if (drain) DrainOrdered();
    }
    template <class Func>
    void AfterPrefix(unsigned lane, Func&& task) {
        // Lane 0 already executes these effects in the original command order.
        if (!lane) {
            task();
            return;
        }
        {
            std::scoped_lock lock{mutex};
            if (!prefix_complete || ordered_running) {
                ordered.emplace_back(std::forward<Func>(task));
                ++deferred_host;
                return;
            }
            // Reserve the ordered stream before dropping the lock. Lane 0 may
            // still be draining older tasks even after prefix_complete is true.
            ordered_running = true;
        }
        task();
        DrainOrdered();
    }
    void Sync() {
        std::unique_lock lock{mutex};
        cv.wait(lock, [this] { return Idle(); });
    }
    // Producer only, after Sync. A raw caller can be the first use of lane 1.
    Buffer Current(unsigned lane) {
        std::scoped_lock lock{mutex};
        assert(Idle());
        if (!buffers[lane]) buffers[lane] = begin(lane);
        return buffers[lane];
    }
    Buffer First() const { return buffers[0]; } // producer only, after Sync
    size_t DeferredHost() const { return deferred_host; } // producer only, after Sync

private:
    bool Idle() const {
        return queues[0].empty() && queues[1].empty() && !busy[0] && !busy[1] &&
               ordered.empty() && !ordered_running;
    }
    // Called by exactly one recording worker, with ordered_running reserved.
    // Tasks are enqueued by lane 1 in stream order, after the Vulkan commands
    // preceding each task have been recorded. Lane 0's marker releases them
    // only after all preceding segment-0 commands and host effects are done.
    void DrainOrdered() {
        while (true) {
            std::function<void()> task;
            {
                std::scoped_lock lock{mutex};
                if (ordered.empty()) {
                    ordered_running = false;
                    return;
                }
                task = std::move(ordered.front());
                ordered.pop_front();
            }
            task();
        }
    }
    void Run(unsigned lane, std::stop_token stop) {
        while (true) {
            std::unique_ptr<Chunk> chunk;
            {
                std::unique_lock lock{mutex};
                sleeping[lane] = true;
                work_cv[lane].wait(lock, stop, [&] { return !queues[lane].empty(); });
                sleeping[lane] = false;
                if (queues[lane].empty()) return;
                chunk = std::move(queues[lane].front());
                queues[lane].pop_front();
                busy[lane] = true;
            }
            // Only this worker uses this lane/pool until Sync has joined it.
            if (!buffers[lane]) buffers[lane] = begin(lane);
            chunk->Execute(buffers[lane]);
            bool idle;
            {
                std::scoped_lock lock{mutex};
                free.push_back(std::move(chunk));
                busy[lane] = false;
                idle = Idle();
            }
            if (idle) cv.notify_all();
        }
    }

    Begin begin;
    std::mutex mutex;
    std::condition_variable_any cv;
    std::array<std::condition_variable_any, 2> work_cv;
    std::array<std::deque<std::unique_ptr<Chunk>>, 2> queues;
    std::array<bool, 2> busy{}, sleeping{};
    std::array<Buffer, 2> buffers{};
    bool prefix_complete = false;
    bool ordered_running = false;
    size_t deferred_host = 0;
    std::deque<std::function<void()>> ordered;
    std::vector<std::unique_ptr<Chunk>> free;
    std::array<std::jthread, 2> threads;
};

} // namespace Vulkan
