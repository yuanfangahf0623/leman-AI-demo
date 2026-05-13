import { Layout } from '@/utils/routerHelper'

const { t } = useI18n()
/**
 * redirect: noredirect        当设置 noredirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'          设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * meta : {
 hidden: true              当设置 true 的时候该路由不会再侧边栏出现 如404，login等页面(默认 false)

 alwaysShow: true          当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式，
 只有一个时，会将那个子路由当做根路由显示在侧边栏，
 若你想不管路由下面的 children 声明的个数都显示你的根路由，
 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，
 一直显示根路由(默认 false)

 title: 'title'            设置该路由在侧边栏和面包屑中展示的名字

 icon: 'svg-name'          设置该路由的图标

 noCache: true             如果设置为true，则不会被 <keep-alive> 缓存(默认 false)

 breadcrumb: false         如果设置为false，则不会在breadcrumb面包屑中显示(默认 true)

 affix: true               如果设置为true，则会一直固定在tag项中(默认 false)

 noTagsView: true          如果设置为true，则不会出现在tag中(默认 false)

 activeMenu: '/dashboard'  显示高亮的路由路径

 followAuth: '/dashboard'  跟随哪个路由进行权限过滤

 canTo: true               设置为true即使hidden为true，也依然可以进行路由跳转(默认 false)
 }
 **/
const tenantEnable = import.meta.env.VITE_APP_TENANT_ENABLE === 'true'

const isTenantRoute = (route: AppRouteRecordRaw) => {
  const name = String(route.name || '')
  const path = route.path || ''
  const title = String(route.meta?.title || '')
  return name.includes('Tenant') || path.includes('/system/tenant') || title.includes('租户')
}

const filterTenantRoutes = (routes: AppRouteRecordRaw[]): AppRouteRecordRaw[] => {
  if (tenantEnable) {
    return routes
  }
  return routes
    .filter((route) => !isTenantRoute(route))
    .map((route) => ({
      ...route,
      children: route.children ? filterTenantRoutes(route.children as AppRouteRecordRaw[]) : route.children
    }))
}

const remainingRouter: AppRouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    name: 'Redirect',
    children: [
      {
        path: '/redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/Redirect/Redirect.vue'),
        meta: {}
      }
    ],
    meta: {
      hidden: true,
      noTagsView: true
    }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/index',
    name: 'Home',
    meta: {},
    children: [
      {
        path: 'index',
        component: () => import('@/views/Home/Index.vue'),
        name: 'Index',
        meta: {
          title: t('router.home'),
          icon: 'ep:home-filled',
          noCache: false,
          affix: true
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    name: 'UserInfo',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'profile',
        component: () => import('@/views/Profile/Index.vue'),
        name: 'Profile',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:user',
          title: t('common.profile')
        }
      },
      {
        path: 'notify-message',
        component: () => import('@/views/system/notify/my/index.vue'),
        name: 'MyNotifyMessage',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:message',
          title: '我的站内信'
        }
      }
    ]
  },
  {
    path: '/system/org',
    component: Layout,
    redirect: '/system/org/dept',
    name: 'SystemOrg',
    meta: {
      title: '组织管理',
      icon: 'ep:office-building',
      alwaysShow: true,
      hidden: true
    },
    children: [
      {
        path: 'dept',
        component: () => import('@/views/system/dept/index.vue'),
        name: 'SystemDept',
        meta: {
          title: '部门管理',
          icon: 'ep:office-building',
          noCache: true
        }
      },
      {
        path: 'post',
        component: () => import('@/views/system/post/index.vue'),
        name: 'SystemPost',
        meta: {
          title: '岗位管理',
          icon: 'ep:postcard',
          noCache: true
        }
      }
    ]
  },
  {
    path: '/system/user',
    component: Layout,
    name: 'SystemUserCenter',
    meta: {
      hidden: true
    },
    children: [
      {
        path: '',
        component: () => import('@/views/system/user/index.vue'),
        name: 'SystemUser',
        meta: {
          title: '用户管理',
          icon: 'ep:user',
          noCache: true
        }
      }
    ]
  },
  {
    path: '/system/tenant',
    component: Layout,
    redirect: '/system/tenant/list',
    name: 'SystemTenantManage',
    meta: {
      title: '租户管理',
      icon: 'ep:management',
      alwaysShow: true
    },
    children: [
      {
        path: 'list',
        component: () => import('@/views/system/tenant/index.vue'),
        name: 'SystemTenant',
        meta: {
          title: '租户列表',
          icon: 'ep:office-building',
          noCache: true
        }
      },
      {
        path: 'package',
        component: () => import('@/views/system/tenantPackage/index.vue'),
        name: 'SystemTenantPackage',
        meta: {
          title: '租户套餐',
          icon: 'ep:collection-tag',
          noCache: true
        }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/role',
    name: 'SystemManage',
    meta: {
      title: '系统管理',
      icon: 'ep:setting',
      alwaysShow: true,
      hidden: true
    },
    children: [
      {
        path: 'role',
        component: () => import('@/views/system/role/index.vue'),
        name: 'SystemRole',
        meta: {
          title: '角色管理',
          icon: 'ep:user-filled',
          noCache: true
        }
      },
      {
        path: 'menu',
        component: () => import('@/views/system/menu/index.vue'),
        name: 'SystemMenu',
        meta: {
          title: '菜单管理',
          icon: 'ep:menu',
          noCache: true
        }
      },
      {
        path: 'dict',
        component: () => import('@/views/system/dict/index.vue'),
        name: 'SystemDictType',
        meta: {
          title: '字典管理',
          icon: 'ep:collection',
          noCache: true
        }
      },
      {
        path: 'operatelog',
        component: () => import('@/views/system/operatelog/index.vue'),
        name: 'SystemOperateLog',
        meta: {
          title: '操作日志',
          icon: 'ep:document',
          noCache: true
        }
      },
      {
        path: 'loginlog',
        component: () => import('@/views/system/loginlog/index.vue'),
        name: 'SystemLoginLog',
        meta: {
          title: '登录日志',
          icon: 'ep:monitor',
          noCache: true
        }
      }
    ]
  },
  {
    path: '/dict',
    component: Layout,
    name: 'dict',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'type/data/:dictType',
        component: () => import('@/views/system/dict/data/index.vue'),
        name: 'SystemDictData',
        meta: {
          title: '字典数据',
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          activeMenu: '/system/dict'
        }
      }
    ]
  },
  {
    path: '/codegen',
    component: Layout,
    name: 'CodegenEdit',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'edit',
        component: () => import('@/views/infra/codegen/EditTable.vue'),
        name: 'InfraCodegenEditTable',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '修改生成配置',
          activeMenu: 'infra/codegen/index'
        }
      }
    ]
  },
  {
    path: '/job',
    component: Layout,
    name: 'JobL',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'job-log',
        component: () => import('@/views/infra/job/logger/index.vue'),
        name: 'InfraJobLog',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '调度日志',
          activeMenu: 'infra/job/index'
        }
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/Login/Login.vue'),
    name: 'Login',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/sso',
    component: () => import('@/views/Login/Login.vue'),
    name: 'SSOLogin',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/social-login',
    component: () => import('@/views/Login/SocialLogin.vue'),
    name: 'SocialLogin',
    meta: {
      hidden: true,
      title: t('router.socialLogin'),
      noTagsView: true
    }
  },
  {
    path: '/403',
    component: () => import('@/views/Error/403.vue'),
    name: 'NoAccess',
    meta: {
      hidden: true,
      title: '403',
      noTagsView: true
    }
  },
  {
    path: '/404',
    component: () => import('@/views/Error/404.vue'),
    name: 'NoFound',
    meta: {
      hidden: true,
      title: '404',
      noTagsView: true
    }
  },
  {
    path: '/500',
    component: () => import('@/views/Error/500.vue'),
    name: 'Error',
    meta: {
      hidden: true,
      title: '500',
      noTagsView: true
    }
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/Error/404.vue'),
    name: '',
    meta: {
      title: '404',
      hidden: true,
      breadcrumb: false
    }
  },
  {
    path: '/bpm',
    component: Layout,
    name: 'bpm',
    meta: {
      title: '工作流',
      icon: 'ep:share',
      alwaysShow: true,
      hidden: true
    },
    children: [
      {
        path: 'process-instance/create',
        component: () => import('@/views/bpm/processInstance/create/index.vue'),
        name: 'BpmProcessInstanceCreate',
        meta: {
          title: '发起流程',
          icon: 'ep:promotion',
          noCache: true
        }
      },
      {
        path: 'process-instance/my',
        component: () => import('@/views/bpm/processInstance/index.vue'),
        name: 'BpmProcessInstanceMy',
        meta: {
          title: '我的流程',
          icon: 'ep:list',
          noCache: true
        }
      },
      {
        path: 'task/my',
        component: () => import('@/views/bpm/task/todo/index.vue'),
        name: 'BpmTodoTask',
        meta: {
          title: '待办任务',
          icon: 'ep:checked',
          noCache: true
        }
      },
      {
        path: 'task/done',
        component: () => import('@/views/bpm/task/done/index.vue'),
        name: 'BpmDoneTask',
        meta: {
          title: '已办任务',
          icon: 'ep:circle-check',
          noCache: true
        }
      },
      {
        path: 'task/copy',
        component: () => import('@/views/bpm/task/copy/index.vue'),
        name: 'BpmProcessInstanceCopy',
        meta: {
          title: '抄送我的',
          icon: 'ep:message',
          noCache: true
        }
      },
      {
        path: 'manager/model',
        component: () => import('@/views/bpm/model/index.vue'),
        name: 'BpmModel',
        meta: {
          title: '流程模型',
          icon: 'ep:connection',
          noCache: true
        }
      },
      {
        path: 'manager/form',
        component: () => import('@/views/bpm/form/index.vue'),
        name: 'BpmForm',
        meta: {
          title: '流程表单',
          icon: 'ep:document',
          noCache: true
        }
      },
      {
        path: 'manager/category',
        component: () => import('@/views/bpm/category/index.vue'),
        name: 'BpmCategory',
        meta: {
          title: '流程分类',
          icon: 'ep:folder-opened',
          noCache: true
        }
      },
      {
        path: 'manager/group',
        component: () => import('@/views/bpm/group/index.vue'),
        name: 'BpmUserGroup',
        meta: {
          title: '用户组',
          icon: 'ep:user-filled',
          noCache: true
        }
      },
      {
        path: 'manager/expression',
        component: () => import('@/views/bpm/processExpression/index.vue'),
        name: 'BpmProcessExpression',
        meta: {
          title: '流程表达式',
          icon: 'ep:operation',
          noCache: true
        }
      },
      {
        path: 'manager/listener',
        component: () => import('@/views/bpm/processListener/index.vue'),
        name: 'BpmProcessListener',
        meta: {
          title: '流程监听器',
          icon: 'ep:bell',
          noCache: true
        }
      },
      {
        path: 'process-instance/manager',
        component: () => import('@/views/bpm/processInstance/manager/index.vue'),
        name: 'BpmProcessInstanceManager',
        meta: {
          title: '流程实例',
          icon: 'ep:data-line',
          noCache: true
        }
      },
      {
        path: 'task/manager',
        component: () => import('@/views/bpm/task/manager/index.vue'),
        name: 'BpmManagerTask',
        meta: {
          title: '任务管理',
          icon: 'ep:finished',
          noCache: true
        }
      },
      {
        path: 'oa/leave',
        component: () => import('@/views/bpm/oa/leave/index.vue'),
        name: 'BpmOALeave',
        meta: {
          title: 'OA 请假',
          icon: 'ep:calendar',
          noCache: true
        }
      },
      {
        path: 'manager/form/edit',
        component: () => import('@/views/bpm/form/editor/index.vue'),
        name: 'BpmFormEditor',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '设计流程表单',
          activeMenu: '/bpm/manager/form'
        }
      },
      {
        path: 'manager/definition',
        component: () => import('@/views/bpm/model/definition/index.vue'),
        name: 'BpmProcessDefinition',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '流程定义',
          activeMenu: '/bpm/manager/model'
        }
      },
      {
        path: 'process-instance/detail',
        component: () => import('@/views/bpm/processInstance/detail/index.vue'),
        name: 'BpmProcessInstanceDetail',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '流程详情',
          activeMenu: '/bpm/task/my'
        },
        props: (route) => ({
          id: route.query.id,
          taskId: route.query.taskId,
          activityId: route.query.activityId
        })
      },
      {
        path: 'process-instance/report',
        component: () => import('@/views/bpm/processInstance/report/index.vue'),
        name: 'BpmProcessInstanceReport',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '数据报表',
          activeMenu: '/bpm/manager/model'
        }
      },
      {
        path: 'oa/leave/create',
        component: () => import('@/views/bpm/oa/leave/create.vue'),
        name: 'OALeaveCreate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '发起 OA 请假',
          activeMenu: '/bpm/oa/leave'
        }
      },
      {
        path: 'oa/leave/detail',
        component: () => import('@/views/bpm/oa/leave/detail.vue'),
        name: 'OALeaveDetail',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '查看 OA 请假',
          activeMenu: '/bpm/oa/leave'
        }
      },
      {
        path: 'manager/model/create',
        component: () => import('@/views/bpm/model/form/index.vue'),
        name: 'BpmModelCreate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '创建流程',
          activeMenu: '/bpm/manager/model'
        }
      },
      {
        path: 'manager/model/:type/:id',
        component: () => import('@/views/bpm/model/form/index.vue'),
        name: 'BpmModelUpdate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '修改流程',
          activeMenu: '/bpm/manager/model'
        }
      }
    ]
  },
  {
    path: '/mall/product', // 商品中心
    component: Layout,
    name: 'ProductCenter',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'spu/add',
        component: () => import('@/views/mall/product/spu/form/index.vue'),
        name: 'ProductSpuAdd',
        meta: {
          noCache: false, // 需要缓存
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '商品添加',
          activeMenu: '/mall/product/spu'
        }
      },
      {
        path: 'spu/edit/:id(\\d+)',
        component: () => import('@/views/mall/product/spu/form/index.vue'),
        name: 'ProductSpuEdit',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '商品编辑',
          activeMenu: '/mall/product/spu'
        }
      },
      {
        path: 'spu/detail/:id(\\d+)',
        component: () => import('@/views/mall/product/spu/form/index.vue'),
        name: 'ProductSpuDetail',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:view',
          title: '商品详情',
          activeMenu: '/mall/product/spu'
        }
      },
      {
        path: 'property/value/:propertyId(\\d+)',
        component: () => import('@/views/mall/product/property/value/index.vue'),
        name: 'ProductPropertyValue',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:view',
          title: '商品属性值',
          activeMenu: '/product/property'
        }
      }
    ]
  },
  {
    path: '/mall/trade', // 交易中心
    component: Layout,
    name: 'TradeCenter',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'order/detail/:id(\\d+)',
        component: () => import('@/views/mall/trade/order/detail/index.vue'),
        name: 'TradeOrderDetail',
        meta: { title: '订单详情', icon: 'ep:view', activeMenu: '/mall/trade/order' }
      },
      {
        path: 'after-sale/detail/:id(\\d+)',
        component: () => import('@/views/mall/trade/afterSale/detail/index.vue'),
        name: 'TradeAfterSaleDetail',
        meta: { title: '退款详情', icon: 'ep:view', activeMenu: '/mall/trade/after-sale' }
      }
    ]
  },
  {
    path: '/member',
    component: Layout,
    name: 'MemberCenter',
    meta: { hidden: true },
    children: [
      {
        path: 'user/detail/:id',
        name: 'MemberUserDetail',
        meta: {
          title: '会员详情',
          noCache: true,
          hidden: true
        },
        component: () => import('@/views/member/user/detail/index.vue')
      }
    ]
  },
  {
    path: '/pay',
    component: Layout,
    name: 'pay',
    meta: { hidden: true },
    children: [
      {
        path: 'cashier',
        name: 'PayCashier',
        meta: {
          title: '收银台',
          noCache: true,
          hidden: true
        },
        component: () => import('@/views/pay/cashier/index.vue')
      }
    ]
  },
  {
    path: '/diy',
    name: 'DiyCenter',
    meta: { hidden: true },
    component: Layout,
    children: [
      {
        path: 'template/decorate/:id',
        name: 'DiyTemplateDecorate',
        meta: {
          title: '模板装修',
          noCache: false,
          hidden: true,
          activeMenu: '/mall/promotion/diy-template/diy-template'
        },
        component: () => import('@/views/mall/promotion/diy/template/decorate.vue')
      },
      {
        path: 'page/decorate/:id',
        name: 'DiyPageDecorate',
        meta: {
          title: '页面装修',
          noCache: false,
          hidden: true,
          activeMenu: '/mall/promotion/diy-template/diy-page'
        },
        component: () => import('@/views/mall/promotion/diy/page/decorate.vue')
      }
    ]
  },
  {
    path: '/crm',
    component: Layout,
    name: 'CrmCenter',
    meta: { hidden: true },
    children: [
      {
        path: 'clue/detail/:id',
        name: 'CrmClueDetail',
        meta: {
          title: '线索详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/clue'
        },
        component: () => import('@/views/crm/clue/detail/index.vue')
      },
      {
        path: 'customer/detail/:id',
        name: 'CrmCustomerDetail',
        meta: {
          title: '客户详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/customer'
        },
        component: () => import('@/views/crm/customer/detail/index.vue')
      },
      {
        path: 'business/detail/:id',
        name: 'CrmBusinessDetail',
        meta: {
          title: '商机详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/business'
        },
        component: () => import('@/views/crm/business/detail/index.vue')
      },
      {
        path: 'contract/detail/:id',
        name: 'CrmContractDetail',
        meta: {
          title: '合同详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/contract'
        },
        component: () => import('@/views/crm/contract/detail/index.vue')
      },
      {
        path: 'receivable-plan/detail/:id',
        name: 'CrmReceivablePlanDetail',
        meta: {
          title: '回款计划详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/receivable-plan'
        },
        component: () => import('@/views/crm/receivable/plan/detail/index.vue')
      },
      {
        path: 'receivable/detail/:id',
        name: 'CrmReceivableDetail',
        meta: {
          title: '回款详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/receivable'
        },
        component: () => import('@/views/crm/receivable/detail/index.vue')
      },
      {
        path: 'contact/detail/:id',
        name: 'CrmContactDetail',
        meta: {
          title: '联系人详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/contact'
        },
        component: () => import('@/views/crm/contact/detail/index.vue')
      },
      {
        path: 'product/detail/:id',
        name: 'CrmProductDetail',
        meta: {
          title: '产品详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/product'
        },
        component: () => import('@/views/crm/product/detail/index.vue')
      }
    ]
  },
  {
    path: '/iot',
    component: Layout,
    name: 'IOT',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'product/product/detail/:id',
        name: 'IoTProductDetail',
        meta: {
          title: '产品详情',
          noCache: true,
          hidden: true,
          activeMenu: '/iot/device/product'
        },
        component: () => import('@/views/iot/product/product/detail/index.vue')
      },
      {
        path: 'device/detail/:id',
        name: 'IoTDeviceDetail',
        meta: {
          title: '设备详情',
          noCache: true,
          hidden: true,
          activeMenu: '/iot/device/device'
        },
        component: () => import('@/views/iot/device/device/detail/index.vue')
      },
      {
        path: 'ota/operation/firmware/detail/:id',
        name: 'IoTOtaFirmwareDetail',
        meta: {
          title: '固件详情',
          noCache: true,
          hidden: true,
          activeMenu: '/iot/operation/ota/firmware'
        },
        component: () => import('@/views/iot/ota/firmware/detail/index.vue')
      }
    ]
  },
  {
    path: '/mes',
    component: Layout,
    name: 'MesWmRouter',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'wm/warehouse/location',
        component: () => import('@/views/mes/wm/warehouse/location/index.vue'),
        name: 'MesWmLocation',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          title: '库区设置',
          activeMenu: '/mes/wm/warehouse'
        }
      },
      {
        path: 'wm/warehouse/area',
        component: () => import('@/views/mes/wm/warehouse/area/index.vue'),
        name: 'MesWmArea',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          title: '库位设置',
          activeMenu: '/mes/wm/warehouse'
        }
      },
      {
        path: 'pro/task/gantt-edit',
        component: () => import('@/views/mes/pro/task/edit/index.vue'),
        name: 'MesProTaskGanttEdit',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          title: '甘特图编辑',
          activeMenu: '/mes/pro/task'
        }
      }
    ]
  }
]

export default filterTenantRoutes(remainingRouter)
