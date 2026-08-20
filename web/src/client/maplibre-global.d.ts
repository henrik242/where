// index.html loads maplibre-gl from a CDN <script>, so it is reachable only as the UMD global
// (see allowUmdGlobalAccess in tsconfig.client.json). Nothing imports the package; the
// devDependency exists solely to type the global at the version index.html pins.
/// <reference types="maplibre-gl" />
