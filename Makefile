STORE_ID := clipcode

.PHONY: run build clean

run:
	flutter pub get && flutter run

build:
	flutter pub get && flutter build apk --release

clean:
	flutter clean
	rm -rf dist

# deploy, release, bump, store-check, store-meta, version
include store.mk
