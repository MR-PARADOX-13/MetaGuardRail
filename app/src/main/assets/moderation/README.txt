Image model attribution

The DEFLATE-compressed nsfw.tflite.deflate asset is the GantMan nsfw_model MobileNetV2 model. It
uses the MIT License in NSFW-MODEL-LICENSE.md. The app extracts it into its private cache before loading.
The model's five output scores are for drawings, hentai, neutral, porn, and
sexy; this app flags a possible sexual/suggestive-image cue when hentai + porn + sexy is at least 0.70.
This model is not a violence detector and may produce false positives or miss content.

Source: https://github.com/GantMan/nsfw_model
