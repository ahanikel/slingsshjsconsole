# Sling SSH-JS console
This is an old tool of mine for getting easy access to the underlying Oak repository
in a Sling instance.

It runs as an OSGi service and listens for SSH connections on port 2222 (configurable).
Upon logging in you get a JavaScript shell which allows you access the Oak java API
directly.

I haven't used this in a long time, so it might need some adjustments to work with
current versions of Oak/Sling.
