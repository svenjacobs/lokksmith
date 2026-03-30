export default {
  extends: ['@commitlint/config-conventional'],
  // Exception for the initial plan commit created by the AI coding agent
  ignores: [(commit) => commit.trim() === 'input: Initial plan'],
  rules: {
    'header-max-length': [1, 'always', 100]
  }
};
