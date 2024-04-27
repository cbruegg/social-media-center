SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cd "$SCRIPT_DIR" || exit 1
docker build -t social-media-server-docker --build-arg "GITHUB_ACTOR=$GITHUB_ACTOR" --build-arg "GITHUB_TOKEN=$GITHUB_TOKEN" .
cd - || exit 1
