# Mobile input follow-up — v0.1, versionCode 16

- Keep the renderer corrections and Mobile layout enabled by default; retain
  user choices and the existing graphics implementation from Build 78.
- Mobile swipepad is enabled by default and can be switched off/on in the player
  menu and Controls settings. Its preference is independent of Classic's pad.
  Turning it off hides the mobile surface without restoring the old Touch button.
- Remove the caption inside the swipepad.
- Apply the visibility predicate when drawing action icons, not only when hit
  testing: Jump and Shake are mutually exclusive according to sensor mode.
- Publish the selected mobile input mode through the atomic Android pad state.
  Synthetic graffiti uses only the right stick in Mobile, including when the
  right stick is neutral. Classic retains its existing left-stick fallback.
- Double camera drag gain and increase spray drag gain. Mobile spray uses a 3%
  linear dead zone instead of applying the physical stick's 15% quadratic curve
  to touch a second time; faster smoothing and a 1.5 rad/s base rate preserve
  the existing bounded per-frame step and angle limits. Classic and SDL physical
  sensor curves retain their previous behavior.
- Right-side camera/fire drags stop when crossing left of the screen midpoint.
  Re-entry sets a fresh origin to avoid an input jump.

Validation includes production Java draw visibility, optional-pad persistence,
independent sticks and gesture boundary handling; production native stick
selection and small-motion curve tests; full Java compilation and runtime suite.
On-device camera feel still needs the user's test.
