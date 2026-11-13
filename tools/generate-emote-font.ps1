# Regenerate the smooth font atlas and matching shader advances together.
# Requires Windows with Segoe UI installed; no downloaded fonts or dependencies.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$atlas = [Drawing.Bitmap]::new(768, 432, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
$graphics = [Drawing.Graphics]::FromImage($atlas)
$font = [Drawing.Font]::new('Segoe UI', 40, [Drawing.FontStyle]::Regular, [Drawing.GraphicsUnit]::Pixel)
$format = [Drawing.StringFormat]::new([Drawing.StringFormat]::GenericTypographic)
try {
    if ($font.Name -ne 'Segoe UI') { throw 'Segoe UI is required to reproduce this atlas.' }
    $graphics.Clear([Drawing.Color]::Black)
    $graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $format.Alignment = [Drawing.StringAlignment]::Center
    $format.LineAlignment = [Drawing.StringAlignment]::Center
    $format.FormatFlags = $format.FormatFlags -bor [Drawing.StringFormatFlags]::MeasureTrailingSpaces
    $widths = for ($code = 32; $code -le 126; $code++) {
        $character = [string][char]$code
        $index = $code - 32
        $bounds = [Drawing.RectangleF]::new(($index % 16) * 48, [Math]::Floor($index / 16) * 72, 48, 72)
        $graphics.DrawString($character, $font, [Drawing.Brushes]::White, $bounds, $format)
        $size = $graphics.MeasureString($character, $font, [Drawing.PointF]::new(0, 0), $format)
        $size.Width.ToString('0.000', [Globalization.CultureInfo]::InvariantCulture)
    }
    $atlas.Save((Join-Path $root 'assets/eris/textures/effect/emote_font.png'), [Drawing.Imaging.ImageFormat]::Png)
    $header = "// Segoe UI, 40 px; advances in the 48 x 72 atlas cells.`nconst float EMOTE_GLYPH_ADVANCE[95] = float[](`n    " + ($widths -join ', ') + "`n);`n"
    [IO.File]::WriteAllText((Join-Path $root 'assets/eris/shaders/include/post/emote_font.glsl'), $header)
} finally {
    $graphics.Dispose()
    $atlas.Dispose()
    $font.Dispose()
    $format.Dispose()
}
