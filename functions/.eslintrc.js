module.exports = {
  env: {
    es6: true,
    node: true,
  },
  parserOptions: {
    "ecmaVersion": 2018,
  },
  extends: [
    "eslint:recommended",
    "google",
  ],
  rules: {
    "no-restricted-globals": ["error", "name", "length"],
    "prefer-arrow-callback": "error",
    "quotes": ["error", "double", { "allowTemplateLiterals": true }],
    "max-len": ["error", { "code": 120 }], // Or your preferred length
    "object-curly-spacing": ["error", "always"], // Example of another common rule
    // Indent, comma-dangle, etc. if you customized them
    "valid-jsdoc": "off", // Crucial for fixing JSDoc errors
    "require-jsdoc": "off", // Crucial for fixing JSDoc errors
  },
  overrides: [
    {
      files: ["**/*.spec.*"],
      env: {
        mocha: true,
      },
      rules: {},
    },
  ],
  globals: {},
};
