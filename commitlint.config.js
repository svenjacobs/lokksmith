export default {
  extends: ['@commitlint/config-conventional'],
  // Exception for the initial plan commit created by the AI coding agent
  ignores: [
    (commit) => ['input: Initial plan', 'Initial plan'].includes(commit.trim()),
    // release-please squash merges carry bot Co-authored-by trailers longer than footer-max-line-length
    (commit) => commit.includes('Co-authored-by: lokksmith-release-please[bot]'),
  ],
  rules: {
    'header-max-length': [1, 'always', 100],
    'body-max-line-length': [0, 'always', 100]
  }
};
