# Keyboard Haver

The #1 app for making you have things when it comes to keyboards

## What this does

This is an Android app you can use to map pressing certain keys on your keyboard to certain places on the touchscreen of your Android phone or tablet.

## What this does **not** do

Keyboard Haver will never register multiple touchscreen touches for a single keyboard keypress. It helps with input mapping, *not* macros or scripted input.

## Setup

1. Root your phone

2. Download [Android Studio](https://developer.android.com/studio)

3. Take an in-game screenshot with on-screen controls enabled

4. Using a drawing program like [Pinta](https://www.pinta-project.com/), open the screenshot you took and write down what the X and Y coordinates are for each of the following keys:
    - Counter-clockwise rotate
    - Clockwise rotate
    - Hold
    - Hard drop
    - Move left
    - Move right
    - Soft drop (down)

5. Open [`KeyboardHaverService.java`](/blob/18a98748c3/app/src/main/java/com/tstman/keyboardhaver/KeyboardHaverService.java#L59-L67) in Android Studio

6. Update lines 59-67 of `KeyboardHaverService.java` so that the button coordinates are the X and Y coordinates that you wrote down from your screenshot

7. Build the KeyboardHaver app and install it on your phone

8. Plug a keyboard into your phone. This is the keyboard that Keyboard Haver will let you have

9. Download a drawing app onto your phone like [Simple Draw Pro](https://play.google.com/store/apps/details?id=com.simplemobiletools.draw.pro)

10.
    i.  Go to Settings -> Accessibility and enable the Keyboard Haver accessibility service (this is what detects the keyboard input to pass on to the touch screen)

    ii. When you get a notification asking if you are okay with the KeyboardHaver app having root access, say *yes* if you are

11. Open the in-game screenshot you took in step 3 in your drawing app (like Simple Draw Pro)

12. Press the keyboard keys for your controls to make sure the touchscreen touches go to the right place. They should overlap with the on-screen controls in the screenshot. Here are the keyboard controls:

    Key | Result
    --- | ---
    Z | Counter-clockwise rotate
    X | Clockwise rotate
    C | Hold
    Spacebar | Hard drop
    Left arrow key | Move left
    Right arrow key | Move right
    Down arrow key | Soft drop

13. Congratulations, you now have a keyboard! Disable the Keyboard Haver accessibility service when you are done using it, because you can't actually use your keyboard to type anything while it is enabled.