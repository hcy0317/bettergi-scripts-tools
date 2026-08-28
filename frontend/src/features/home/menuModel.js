export const resolveMenuEnvironment = (configuredEnvironment, isDevelopment) =>
  configuredEnvironment || (isDevelopment ? 'dev' : 'prod')

export const routeGroupOrder = (groupConfig, groupName) =>
  groupConfig.get(groupName)?.order ?? 999

export const visibleRouteGroups = (groups, groupConfig, environment) =>
  groups.filter(groupName => groupConfig.get(groupName)?.env?.includes(environment))
