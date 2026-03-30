export default {
  extends: ['@commitlint/config-conventional'],
  // Exception for the initial plan commit created by the AI coding agent
  ignores: [(commit) => ['input: Initial plan', 'Initial plan'].includes(commit.trim())],
  rules: {
    'header-max-length': [1, 'always', 100]
  }
};
