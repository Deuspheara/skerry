import CoreGraphics

/// A damped spring integrated in small fixed steps, for settling the player sheet from the finger's velocity. Pure maths, so its behaviour is tested:
/// the value starts where the finger let go, with the finger's velocity, and comes to rest on the target (an interrupted settle simply starts again from the
/// current value and velocity, which is what makes the sheet interruptible).
struct SpringMath: Equatable {
    var stiffness: CGFloat = 420
    /// About 0.9 of critical damping: a hint of overshoot for a thrown sheet, none for a slow one.
    var damping: CGFloat = 37

    struct State: Equatable {
        var value: CGFloat
        var velocity: CGFloat
    }

    func advance(_ state: State, target: CGFloat, dt: CGFloat) -> State {
        var s = state
        let steps = max(1, Int((dt / (1.0 / 240)).rounded(.up)))
        let h = dt / CGFloat(steps)
        for _ in 0..<steps {
            let acceleration = -stiffness * (s.value - target) - damping * s.velocity
            s.velocity += acceleration * h
            s.value += s.velocity * h
        }
        return s
    }

    func isSettled(_ state: State, target: CGFloat) -> Bool {
        abs(state.value - target) < 0.0005 && abs(state.velocity) < 0.005
    }
}
