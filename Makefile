.PHONY: all run test clean start

VERSION=$(shell jq -r .version developer-portal-ui/package.json)
ARC42_SRC = $(shell find docs/arc42/src)
PLANTUML_SRC = $(shell find docs/arc42/diagrams -type f -name '*.puml')
DEPENDENCIES = jq npm plantuml asciidoctor docker-compose mvn docker

build-services: build-java-services build-ui-services build-arc-42  ## Build all services

## Install section ##
install:   ##Install developer tools

ifeq ($(shell uname),Darwin)
	make install-for-MacOS
else ifeq ($(shell uname),Linux)
	make install-for-Linux
else
	@echo "Doesn't support your OS"
endif

# Install developer tools for Linux
install-for-Linux:
	sudo apt-get install jq
	sudo apt-get install plantuml
	sudo apt-get install -y asciidoctor

# Install developer tools for MacOS
install-for-MacOS:
	curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install | ruby
	brew install jq
	brew install plantuml
	brew install asciidoctor

# Lint section

lint-all: lint-tpp-ui lint-oba-ui lint-developer-portal-ui lint-tpp-rest-server lint-online-banking lint-certificate-generator lint-docker-compose #lint all services

lint-tpp-ui:
	find tpp-ui -type f -name "*.json" -exec jsonlint -qc {} \; # lint all json
	find tpp-ui -type f \( -name "*.yml" -o -name "*.yaml" \) -exec yamllint -d "{extends: relaxed, rules: {line-length: {max: 160}}}" {} \;
	find tpp-ui -type f \( -iname "*.xml" ! -iname pom.xml \) -exec xmllint --noout {} \;
	#cd tpp-ui && npm run lint 
	#cd tpp-ui && npm run prettier-check
	docker run --rm -i hadolint/hadolint < tpp-ui/Dockerfile

lint-oba-ui:
	cd oba-ui
	find oba-ui -type f -name "*.json" -exec jsonlint -qc {} \; # lint all json
	find oba-ui -type f \( -name "*.yml" -o -name "*.yaml" \) -exec yamllint -d "{extends: relaxed, rules: {line-length: {max: 160}}}" {} \;
	find oba-ui -type f \( -iname "*.xml" ! -iname pom.xml \) -exec xmllint --noout {} \;
	#cd oba-ui && npm install
	#cd oba-ui && npm run lint 
	#cd oba-ui && npm run prettier-check
	docker run --rm -i hadolint/hadolint < oba-ui/Dockerfile

lint-developer-portal-ui:
	find developer-portal-ui -type f -name "*.json" -exec jsonlint -qc {} \; # lint all json
	find developer-portal-ui -type f \( -name "*.yml" -o -name "*.yaml" \) -exec yamllint -d "{extends: relaxed, rules: {line-length: {max: 160}}}" {} \;
	find developer-portal-ui -type f \( -iname "*.xml" ! -iname pom.xml \) -exec xmllint --noout {} \;
	cd developer-portal-ui && npm install
	cd developer-portal-ui && npm run lint 
	cd developer-portal-ui && npm run prettier-check
	docker run --rm -i hadolint/hadolint < developer-portal-ui/Dockerfile

lint-tpp-rest-server:
	find tpp-app -type f -name "*.json" -exec jsonlint -qc {} \; # lint all json
	find tpp-app -type f \( -name "*.yml" -o -name "*.yaml" \) -exec yamllint -d "{extends: relaxed, rules: {line-length: {max: 160}}}" {} \;
	find tpp-app -type f \( -iname "*.xml" ! -iname pom.xml \) -exec xmllint --noout {} \;
	docker run --rm -i hadolint/hadolint < tpp-app/tpp-rest-server/Dockerfile

lint-online-banking:
	find online-banking -type f -name "*.json" -exec jsonlint -qc {} \; # lint all json
	find online-banking -type f \( -name "*.yml" -o -name "*.yaml" \) -exec yamllint -d "{extends: relaxed, rules: {line-length: {max: 160}}}" {} \;
	find online-banking -type f \( -iname "*.xml" ! -iname pom.xml \) -exec xmllint --noout {} \;
	docker run --rm -i hadolint/hadolint < online-banking/online-banking-app/Dockerfile

lint-certificate-generator:
	find certificate-generator -type f -name "*.json" -exec jsonlint -qc {} \; # lint all json
	find certificate-generator -type f \( -name "*.yml" -o -name "*.yaml" \) -exec yamllint -d "{extends: relaxed, rules: {line-length: {max: 160}}}" {} \;
	find certificate-generator -type f \( -iname "*.xml" ! -iname pom.xml \) -exec xmllint --noout {} \;
	docker run --rm -i hadolint/hadolint < certificate-generator/Dockerfile

lint-docker-compose:
	docker-compose -f docker-compose.yml -f docker-compose-build.yml -f docker-compose-google-analytics-enabled.yml -f docker-compose-no-certificate-generator.yml -f docker-compose-xs2a-embedded.yml config  -q
	mvn validate
	yamllint -d "{extends: relaxed, rules: {line-length: {max: 160}}}" bank-profile/*.xml

## Run section ##
run:  ## Run services from Docker Hub without building:
	docker-compose pull && docker-compose up

start: ## Run everything with docker-compose without building
	docker-compose -f docker-compose-build.yml up

all: build-services ## Run everything with docker-compose after building
	docker-compose -f docker-compose-build.yml up --build

## Build section ##
build-java-services: ## Build java services
	mvn -DskipTests clean package

build-ui-services: npm-install-tpp-ui npm-install-oba-ui npm-install-developer-portal-ui ## Build ui services

npm-install-tpp-ui: tpp-ui/package.json tpp-ui/package-lock.json ## Install TPP-UI NPM dependencies
	cd tpp-ui && npm install && npm run build

npm-install-oba-ui: oba-ui/package.json oba-ui/package-lock.json ## Install OBA-UI NPM dependencies
	cd oba-ui && npm install && npm run build

npm-install-developer-portal-ui: developer-portal-ui/package.json developer-portal-ui/package-lock.json ## Install DEV-PORTAL-UI NPM dependencies
	cd developer-portal-ui && npm install && npm run build

## Unit tests section
unit-tests-all-frontend:

unit-tests-oba-ui:

unit-tests-tpp-ui:

unit-tests-develpoer-portal-ui:


unit-tests-backend:


## Integration tests section


## Build arc42
build-arc-42: arc42/images/generated $(ARC42_SRC) docs/arc42/xs2a-sandbox-arc42.adoc developer-portal-ui/package.json ## Generate arc42 html documentation
	cd docs/arc42 && asciidoctor -a acc-version=$(VERSION) xs2a-sandbox-arc42.adoc

arc42/images/generated: $(PLANTUML_SRC) ## Generate images from .puml files
# Note: Because plantuml doesnt update the images/generated timestamp we need to touch it afterwards
	cd docs/arc42 && mkdir -p images/generated && plantuml -o "../images/generated" diagrams/*.puml && touch images/generated

## Tests section ##
test: test-java-services ## Run all tests

test-java-services: ## Run java tests
	mvn test

test-ui-services: ## Run tests in UI
	cd tpp-ui && npm run test-single-headless
	cd oba-ui && npm run test-single-headless
	cd developer-portal-ui && npm run test-single-headless

## Clean section ##
clean: clean-java-services clean-ui-services ## Clean everything

clean-java-services: ## Clean services temp files
	mvn clean

clean-ui-services: ## Clean UI temp files
	cd tpp-ui && rm -rf dist
	cd oba-ui && rm -rf dist
	cd developer-portal-ui && rm -rf dist

## Check section ##
check: ## Check required dependencies ("@:" hides nothing to be done for...)
	@: $(foreach exec,$(DEPENDENCIES),\
          $(if $(shell command -v $(exec) 2> /dev/null ),$(info (OK) $(exec) is installed),$(info (FAIL) $(exec) is missing)))

## Help section ##
help: ## Display this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n\nTargets:\n"} /^[a-zA-Z0-9_\-\/\. ]+:.*?##/ { printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
