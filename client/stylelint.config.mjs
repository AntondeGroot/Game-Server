/** @type {import('stylelint').Config} */
export default {
  extends: ['stylelint-config-standard-scss'],
  plugins: ['./stylelint/hover-needs-fine-pointer.mjs'],
  rules: {
    'gameroom/hover-needs-fine-pointer': true,
  },
};
