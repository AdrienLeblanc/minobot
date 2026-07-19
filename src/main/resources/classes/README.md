# Class icons

One SVG per Dofus Retro class **and sex**, loaded by `SwingOverlay` (rendered with jsvg) and shown on
each character's overlay row and in the class picker. The leaf of each file is the class's own name in
lower case, an underscore, and the sex's one-letter suffix (`m` / `f`) — this is the convention
`DofusClass.iconResource(Sex)` builds, and the only thing the loader looks for:

```
cra_m.svg      cra_f.svg       enutrof_m.svg   enutrof_f.svg
iop_m.svg      iop_f.svg       feca_m.svg      feca_f.svg
sadida_m.svg   sadida_f.svg    xelor_m.svg     xelor_f.svg
osamodas_m.svg osamodas_f.svg  eniripsa_m.svg  eniripsa_f.svg
sacrieur_m.svg sacrieur_f.svg  pandawa_m.svg   pandawa_f.svg
ecaflip_m.svg  ecaflip_f.svg   sram_m.svg      sram_f.svg
```

Square viewBoxes read best (each is drawn into a square cell, and being vectors they stay crisp at every
overlay scale). A class/sex whose SVG is missing is not an error: the panel falls back to a lettered
badge, exactly as the application logo falls back to its wordmark when `logo.png` is absent.
