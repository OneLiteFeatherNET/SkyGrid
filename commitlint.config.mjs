// Conventional Commits enforcement for the pr-lint workflow (commits and PR title).
// https://www.conventionalcommits.org
export default {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "body-max-line-length": [0, "always"],
    "footer-max-line-length": [0, "always"],
    "header-max-length": [2, "always", 100],
  },
};
