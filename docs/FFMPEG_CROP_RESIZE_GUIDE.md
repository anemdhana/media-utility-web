# FFmpeg Guide: Crop and Resize Video

This guide explains how to crop and resize videos with `ffmpeg`. It is an original summary inspired by the Bannerbear article:
<https://www.bannerbear.com/blog/how-to-crop-resize-a-video-using-ffmpeg/>

---

## Prerequisites

Make sure `ffmpeg` is installed and available in your terminal.

```bash
ffmpeg -version
```

Example input file used below:

```bash
input.mp4
```

---

## 1. Resize a Video

Use the `scale` filter to change the output resolution.

### Resize to a fixed width and height

```bash
ffmpeg -i input.mp4 -vf "scale=1280:720" output_720p.mp4
```

- `1280` = output width
- `720` = output height

> This forces the video into the exact dimensions, which may stretch the image if the aspect ratio changes.

### Resize while keeping aspect ratio

Set one dimension and let `ffmpeg` calculate the other automatically.

```bash
ffmpeg -i input.mp4 -vf "scale=1280:-1" output_width_1280.mp4
```

or

```bash
ffmpeg -i input.mp4 -vf "scale=-1:720" output_height_720.mp4
```

- `-1` tells `ffmpeg` to preserve the original aspect ratio.

### Resize by percentage

```bash
ffmpeg -i input.mp4 -vf "scale=iw*0.5:ih*0.5" output_half_size.mp4
```

- `iw` = input width
- `ih` = input height

This example scales the video to 50% of its original size.

---

## 2. Crop a Video

Use the `crop` filter to remove unwanted areas.

Basic syntax:

```bash
crop=output_width:output_height:x:y
```

Where:
- `output_width` = width of the cropped area
- `output_height` = height of the cropped area
- `x` = horizontal starting point
- `y` = vertical starting point

### Crop a specific area

```bash
ffmpeg -i input.mp4 -vf "crop=640:360:100:50" cropped.mp4
```

This command:
- crops the video to `640x360`
- starts `100` pixels from the left
- starts `50` pixels from the top

### Crop from the center

```bash
ffmpeg -i input.mp4 -vf "crop=640:360:(in_w-640)/2:(in_h-360)/2" centered_crop.mp4
```

- `in_w` = input width
- `in_h` = input height

This keeps the crop centered automatically.

---

## 3. Crop and Resize in One Command

You can chain filters together.

```bash
ffmpeg -i input.mp4 -vf "crop=800:800:(in_w-800)/2:(in_h-800)/2,scale=500:500" output_square.mp4
```

This command:
1. crops a centered square area
2. resizes it to `500x500`

---

## 4. Preserve Aspect Ratio with Padding

If you want a fixed frame size without stretching, scale first and then pad.

```bash
ffmpeg -i input.mp4 -vf "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2" padded_output.mp4
```

This:
- resizes the video to fit inside `1280x720`
- adds padding where needed
- avoids distortion

---

## 5. Useful Tips

### Check video resolution

```bash
ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=s=x:p=0 input.mp4
```

### Use a higher quality encode

```bash
ffmpeg -i input.mp4 -vf "scale=1280:-1" -c:v libx264 -crf 18 -preset medium -c:a copy output.mp4
```

### Overwrite output automatically

```bash
ffmpeg -y -i input.mp4 -vf "scale=1280:-1" output.mp4
```

---

## 6. Common Examples

### Convert landscape video to square

```bash
ffmpeg -i input.mp4 -vf "crop=ih:ih:(iw-ih)/2:0" square.mp4
```

### Create a vertical 9:16 output

```bash
ffmpeg -i input.mp4 -vf "crop=ih*9/16:ih:(iw-ih*9/16)/2:0,scale=1080:1920" vertical.mp4
```

### Shrink a video for web use

```bash
ffmpeg -i input.mp4 -vf "scale=854:-1" -c:v libx264 -crf 23 -preset fast -c:a aac -b:a 128k web_output.mp4
```

---

## 7. Summary

The most common `ffmpeg` filters for this workflow are:

- `scale` → resize video
- `crop` → cut out part of the frame
- `pad` → add borders to fit a target size

A simple rule of thumb:
- use `scale=width:-1` to resize safely
- use `crop=w:h:x:y` to trim the frame
- combine both when preparing content for social or web formats

---

## Reference

- Bannerbear tutorial: <https://www.bannerbear.com/blog/how-to-crop-resize-a-video-using-ffmpeg/>
