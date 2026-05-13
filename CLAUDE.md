# Old School Collage Landing Page

The overall idea is that this is a landing page for access to other websites and pages, including a sign-up form (not yet implemented)


## Overall Visuals
- The site images can be relatively medium (~160000px^2 area)
- We are going to want the standard 1280?px content area (live area? 1280 grid? Safe Area? Tell me what you want me to call it, Claude)
  - Background will still be full vp
  - Site images will be clustered (mostly) in content area
  - Better depth shadowing with stacked stickers
- Somehow we will want to make the site images stand out more than the collage images
  - Each site image has a colored airbrush glow that radiates uniformly from all edges
    - Glow color matches the letter-sticker label color for that image
    - Implemented as stacked CSS `drop-shadow` filters on a wrapper element (parent of
      the clip-path element), so the glow correctly escapes the irregular clip-path shape
    - 5 layers: 4px→10px→22px→40px→65px blur, opacity 0.95→0.80→0.55→0.30→0.15
    - Glow is applied only after all site images have finished loading, when scraps are revealed
    - Site scraps also carry a stronger double black drop-shadow (alpha 0.55–0.75) to
      appear more lifted than background scraps
    - A subtle white linear-gradient overlay (135°, 22%→transparent at 55%) on the
      upper-left edge simulates a light catch (reverse shadow)
  -  Future possibilities
    1) Transparency on content div (1280px wide placeholder for site images)
    2) Tiny core that matches curve of image for cuts
    3) bedazzle stickers (this would be like )
       - narrow lines, perhaps multiple lined up on edge
       - long narrow squigglies,
       - "corner pieces"
    4) Better shadowing
    5) Reverse shadowing
  6) Something else
- The core width should be relative to image width, not original image width before scaling

## Site Images
- Location: landing/images/sites
- Scraps of paper similar to bg images (below), in that they are pasted on top of the bg (clearly separate layers)
- They will need to be mostly visible, and not cover each other more than a confgured percent (they can completely cover bg images)
- Clicking on the image will bring it to the top, in cases where other pieces are partially covering them
- The edges should look cut/torn, though let's have the scraps be scaled images
  - Before scaling, we might randomly (30% chance) do x-axis or y-exis cropping
- There will be links on the images that go to the respective page/sites
  - These will be like letter stickers placed on top of the image
  - DNI: perhaps we will visit pasting "outlines" for viewability against bg
  - the letters should be placed as though middle schooler put them, and can be diaganol, up and down, or normal
  - It would be great if we could have different fonts, different colors
    - Consistent font/color for readability per link
    - Future possibility: [image](https://m.media-amazon.com/images/I/7119GIXtvOL._AC_SL1485_.jpg)

## Site Links
ELEMENT element.techietable.com element.webp
FIVE THINGS fivethings.techietable.com fivethings.webp
INFONTITY infontityscroll.com infontity.webp
SKYISOPEN skyisopen.techietable.com skyisopen.webp


## Images
- In the images folder, there are <image count> images (may be more than max, code needs to count).
- Image file name are fragmentXX.png
- These images are like magazine pages that are sources for our "scraps"
- The scraps should look as much as possible as though they were cut or ripped by a middle school student
  - Edges should be procedurally generated on each load/reload
  - randomly, 0-2 edges should be "Ripped" and the others "cut"
  - Images or scraps should never be scaled -- resizing comes from cropping
- Discard and recut scraps that have too much white or too much black (boring threshold configurable)
- The background should look like an authentic medium, with a dropdown to select (possible just for dev)
  - Kraft paper
  - Corkboard
  - White Posterboard
  - Notebook paper
  - Papyrus

## Collage
- We need to find a way to paste the scraps on the medium
  - in a way that is artistic
  - not a grid or masonry design
  - The code logic we use might be key
    - Default: Stochastic Packing
    - Also may explore
      - Brute Force Circle Packing
      - Apollonian Design
      - Weighted Centroidal Voronoi Tiling
      - What else might be better?
- There should be little bg showing
- The scraps can cover each other up
  - soft overlap constraints
  - Percent should be set by dropdown with default (below)
    - 60/55/50/45/40/35/30/25
- Proper shadowing should apply
- Images may go off-screen, or be "cut" just before going off, like on the edge of the medium

## Tech Stack
- Frontend: HTML / CSS / JS (3 files: index.html, style.css, collage.js)
- Backend: Spring Boot API
  - Generates the background collage server-side and returns it as a single WebP image
  - Frontend fetches `GET /api/background?w=<vw>&h=<vh>` and sets it as a CSS background-image
  - All bg scrap logic lives here: fragment selection, boring-crop detection, clip-path masking, placement, shadowing
  - `BackgroundCache` pre-generates a 2024×2024 image at startup and serves it immediately on request, queuing the next generation in the background (serve-then-regenerate pattern)
  - During generation the cache also pre-encodes two WebP byte arrays: mobile (1024×1024) and full (2024×2024), both at quality 0.75 lossy
  - Requests with `max(w,h) ≤ 1024` receive the mobile bytes; larger viewports receive the full bytes — zero encoding work on the request thread
  - Encoded as WebP (via `org.sejda.imageio:webp-imageio`) rather than PNG for significantly smaller download size
  - Fragment PNGs (~14MB total) are never sent to the browser; only the composed WebP is
  - Backend is shared infrastructure — may be reused for other collage-based applications
- Frontend retains full responsibility for the interactive foreground layer:
  - Site image scraps with glows, labels, click-to-front z-ordering
  - Site image fetches begin in parallel with the bg fetch; site layer is built immediately after bg arrives but scraps stay hidden until all images settle
  - Loading is a two-phase reveal: bg arrival fades the spinner overlay to transparent (bg becomes visible, spinner scrap keeps spinning); all scraps reveal together and spinner is removed only after the last site image settles (load or error)
  - Background type dropdown (passes selection to backend as a query param)
  - Overlap % dropdown
- Must work on desktop and mobile
- Iteration number added to each creation/change for reference

## Future Possibilities
- Subtle animation
- Explore Canvas
- Soft Overlap Constraints

## Configuration

- Site image cover percentage: 10
- Number of images used: 40
- soft overlap constraints: 50%
- Scrap sizing: longer dimension 10–35% of viewport width; aspect ratio 0.15–1.0 (shorter ÷ longer); orientation random
- Percent of sides cut
  - 0: 60
  - 1: 30
  - 2: 10
- Background: corkboard
- Boring Threshold: default 60%