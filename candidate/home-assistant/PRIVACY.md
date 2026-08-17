# Privacy

This unofficial plugin connects directly from the Android phone to the Home
Assistant URL configured by the user. The URL and long-lived access token are kept
in the plugin's private app storage; the token is protected with Android Keystore.

The token never enters the Nexus plugin descriptor, a HUD surface, logs, or a
glasses payload. Entity state and configured dashboard data are processed locally
to render the selected Nexus surface. The plugin does not operate an external
service and does not send Home Assistant credentials or state to the plugin author.

Removing the plugin deletes its private Android app data. Revoking the long-lived
access token in Home Assistant immediately prevents further access with that token.

