
#! /bin/bash

git pull
./gradlew distDocker
cd ./ansible
ansible-playbook -i environments/local wipe.yml
sleep 20
ansible-playbook -i environments/local controller.yml -e mode=clean
sleep 20
ansible-playbook -i environments/local controller.yml
sleep 10
curl --insecure --location --request PUT 'http://h2871438.stratoserver.net/api/v1/namespaces/_/actions/hello7?overwrite=true' \
--header 'Authorization: Basic MjNiYzQ2YjEtNzFmNi00ZWQ1LThjNTQtODE2YWE0ZjhjNTAyOjEyM3pPM3haQ0xyTU42djJCS0sxZFhZRnBYbFBrY2NPRnFtMTJDZEFzTWdSVTRWck5aOWx5R1ZDR3VNREdJd1A=' \
--header 'Content-Type: application/json' \
--data-raw '{
    "namespace": "_",
    "name": "hello7",
    "exec": {
        "kind": "nodejs:10",
        "code": "function main(params) { return {payload:\"Hello \"+params.name}}"
    }
}'
sleep 10
ansible-playbook -i environments/local logs.yml
